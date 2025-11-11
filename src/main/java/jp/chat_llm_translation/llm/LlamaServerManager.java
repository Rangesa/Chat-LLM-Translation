package jp.chat_llm_translation.llm;

import jp.chat_llm_translation.Chat_llm_translation;
import jp.chat_llm_translation.config.ModConfig;
import jp.chat_llm_translation.downloader.ModelDownloader;
import net.fabricmc.loader.api.FabricLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * llama-serverプロセスを管理するクラス
 * MOD起動時に自動的にllama-serverを起動し、MOD終了時に停止します
 */
public class LlamaServerManager {
    private Process serverProcess;
    private final ModConfig config;
    private Thread outputReaderThread;
    private Thread errorReaderThread;
    private volatile boolean isRunning = false;

    public LlamaServerManager() {
        this.config = ModConfig.getInstance();
    }

    /**
     * llama-serverを起動
     *
     * @return 起動成功の場合true
     */
    public CompletableFuture<Boolean> startServer() {
        return CompletableFuture.supplyAsync(() -> {
            if (!config.autoStartLlamaServer) {
                Chat_llm_translation.LOGGER.info("[ChatLLM] Auto-start llama-server is disabled");
                return false;
            }

            if (isRunning && serverProcess != null && serverProcess.isAlive()) {
                Chat_llm_translation.LOGGER.info("[ChatLLM] llama-server is already running");
                return true;
            }

            try {
                Path llamaServerPath = ModelDownloader.getLlamaServerPath();

                // 設定からモデルファイルを取得
                Path modelsDir = ModelDownloader.getModelPath().getParent();
                Path modelPath = modelsDir.resolve(config.llamaModelFile);

                // llama-serverが存在するか確認
                if (!Files.exists(llamaServerPath)) {
                    Chat_llm_translation.LOGGER.error("[ChatLLM] llama-server not found at: {}", llamaServerPath);
                    return false;
                }

                // モデルファイルが存在するか確認
                if (!Files.exists(modelPath)) {
                    Chat_llm_translation.LOGGER.error("[ChatLLM] Model file not found at: {}", modelPath);
                    return false;
                }

                // コマンドライン引数を構築
                List<String> command = buildCommand(llamaServerPath, modelPath);

                Chat_llm_translation.LOGGER.info("[ChatLLM] Starting llama-server with command: {}", String.join(" ", command));

                // プロセスビルダーを作成
                ProcessBuilder processBuilder = new ProcessBuilder(command);
                processBuilder.directory(llamaServerPath.getParent().toFile());

                // 環境変数を設定（マルチGPU対応）
                if (config.llamaGpuId >= 0) {
                    processBuilder.environment().put("CUDA_VISIBLE_DEVICES", String.valueOf(config.llamaGpuId));
                    Chat_llm_translation.LOGGER.info("[ChatLLM] Setting CUDA_VISIBLE_DEVICES={}", config.llamaGpuId);
                }

                // プロセスを起動
                serverProcess = processBuilder.start();
                isRunning = true;

                // 標準出力を読み取るスレッド
                outputReaderThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (config.debugMode) {
                                Chat_llm_translation.LOGGER.info("[llama-server] {}", line);
                            }
                        }
                    } catch (IOException e) {
                        if (isRunning) {
                            Chat_llm_translation.LOGGER.error("[ChatLLM] Error reading llama-server output", e);
                        }
                    }
                }, "llama-server-output-reader");
                outputReaderThread.setDaemon(true);
                outputReaderThread.start();

                // 標準エラー出力を読み取るスレッド
                errorReaderThread = new Thread(() -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(serverProcess.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            Chat_llm_translation.LOGGER.warn("[llama-server] {}", line);
                        }
                    } catch (IOException e) {
                        if (isRunning) {
                            Chat_llm_translation.LOGGER.error("[ChatLLM] Error reading llama-server error output", e);
                        }
                    }
                }, "llama-server-error-reader");
                errorReaderThread.setDaemon(true);
                errorReaderThread.start();

                // プロセス終了を監視するスレッド
                Thread watchdogThread = new Thread(() -> {
                    try {
                        int exitCode = serverProcess.waitFor();
                        isRunning = false;
                        if (exitCode != 0) {
                            Chat_llm_translation.LOGGER.error("[ChatLLM] llama-server exited with code: {}", exitCode);
                        } else {
                            Chat_llm_translation.LOGGER.info("[ChatLLM] llama-server stopped normally");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }, "llama-server-watchdog");
                watchdogThread.setDaemon(true);
                watchdogThread.start();

                // サーバーが起動するまで少し待つ
                Thread.sleep(2000);

                Chat_llm_translation.LOGGER.info("[ChatLLM] llama-server started successfully on port {}", config.llamaServerPort);
                return true;

            } catch (Exception e) {
                Chat_llm_translation.LOGGER.error("[ChatLLM] Failed to start llama-server", e);
                isRunning = false;
                return false;
            }
        });
    }

    /**
     * llama-serverを停止
     */
    public void stopServer() {
        if (serverProcess != null && serverProcess.isAlive()) {
            Chat_llm_translation.LOGGER.info("[ChatLLM] Stopping llama-server...");
            isRunning = false;

            try {
                // 正常終了を試みる
                serverProcess.destroy();

                // 最大5秒待つ
                if (!serverProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    Chat_llm_translation.LOGGER.warn("[ChatLLM] llama-server did not stop gracefully, forcing shutdown...");
                    serverProcess.destroyForcibly();
                }

                Chat_llm_translation.LOGGER.info("[ChatLLM] llama-server stopped");
            } catch (InterruptedException e) {
                Chat_llm_translation.LOGGER.error("[ChatLLM] Error stopping llama-server", e);
                serverProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }

            serverProcess = null;
        }
    }

    /**
     * llama-serverが実行中かどうかを確認
     *
     * @return 実行中の場合true
     */
    public boolean isRunning() {
        return isRunning && serverProcess != null && serverProcess.isAlive();
    }

    /**
     * コマンドライン引数を構築
     *
     * @param llamaServerPath llama-serverの実行ファイルパス
     * @param modelPath モデルファイルパス
     * @return コマンドライン引数のリスト
     */
    private List<String> buildCommand(Path llamaServerPath, Path modelPath) {
        List<String> command = new ArrayList<>();

        // 実行ファイル
        command.add(llamaServerPath.toAbsolutePath().toString());

        // モデルファイル
        command.add("--model");
        command.add(modelPath.toAbsolutePath().toString());

        // ポート
        command.add("--port");
        command.add(String.valueOf(config.llamaServerPort));

        // コンテキストサイズ
        command.add("--ctx-size");
        command.add(String.valueOf(config.llamaContextSize));

        // GPU層数
        command.add("--n-gpu-layers");
        command.add(String.valueOf(config.llamaGpuLayers));

        // バッチサイズ
        command.add("--batch-size");
        command.add(String.valueOf(config.llamaBatchSize));

        // スレッド数
        command.add("--threads");
        command.add(String.valueOf(config.llamaThreads));

        // 並列処理数
        command.add("--parallel");
        command.add(String.valueOf(config.llamaParallel));

        // メインGPU
        command.add("--main-gpu");
        command.add(String.valueOf(config.llamaMainGpu));

        // ホスト
        command.add("--host");
        command.add(config.llamaServerHost);

        // メトリクス
        if (config.llamaMetrics) {
            command.add("--metrics");
        }

        return command;
    }
}
