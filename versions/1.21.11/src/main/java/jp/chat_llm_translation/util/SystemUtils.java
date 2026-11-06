package jp.chat_llm_translation.util;

import jp.chat_llm_translation.Chat_llm_translation;
import jp.chat_llm_translation.downloader.ModelDownloader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * システム情報を取得するユーティリティクラス
 */
public class SystemUtils {

    /**
     * 利用可能なGPUのリストを取得
     *
     * @return GPU IDとGPU名のリスト（例: "GPU 0: NVIDIA GeForce RTX 4090"）
     */
    public static List<String> getAvailableGPUs() {
        List<String> gpus = new ArrayList<>();

        try {
            // nvidia-smiコマンドでGPU情報を取得
            ProcessBuilder pb = new ProcessBuilder("nvidia-smi", "--query-gpu=index,name", "--format=csv,noheader");
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(",", 2);
                    if (parts.length == 2) {
                        String gpuId = parts[0].trim();
                        String gpuName = parts[1].trim();
                        gpus.add("GPU " + gpuId + ": " + gpuName);
                    }
                }
            }

            process.waitFor();

            if (gpus.isEmpty()) {
                // nvidia-smiが失敗した場合のフォールバック
                gpus.add("GPU 0: Default GPU");
            }
        } catch (Exception e) {
            Chat_llm_translation.LOGGER.warn("[ChatLLM] Failed to detect GPUs, using default: {}", e.getMessage());
            gpus.add("GPU 0: Default GPU");
        }

        // "すべてのGPU"オプションを追加
        gpus.add(0, "All GPUs (-1)");

        return gpus;
    }

    /**
     * GPU IDをGPU名から抽出
     *
     * @param gpuString GPU文字列（例: "GPU 1: NVIDIA GeForce RTX 4090"）
     * @return GPU ID（例: 1）、"All GPUs"の場合は-1
     */
    public static int extractGpuId(String gpuString) {
        if (gpuString.startsWith("All GPUs")) {
            return -1;
        }

        try {
            // "GPU 1: ..." から "1" を抽出
            String[] parts = gpuString.split(":");
            if (parts.length > 0) {
                String idPart = parts[0].replace("GPU", "").trim();
                return Integer.parseInt(idPart);
            }
        } catch (Exception e) {
            Chat_llm_translation.LOGGER.error("[ChatLLM] Failed to parse GPU ID from: {}", gpuString, e);
        }

        return -1;
    }

    /**
     * 利用可能なモデルファイルのリストを取得
     *
     * @return モデルファイル名のリスト
     */
    public static List<String> getAvailableModels() {
        List<String> models = new ArrayList<>();

        try {
            Path modelsDir = ModelDownloader.getModelPath().getParent();

            if (Files.exists(modelsDir) && Files.isDirectory(modelsDir)) {
                models = Files.list(modelsDir)
                        .filter(path -> path.toString().endsWith(".gguf"))
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .collect(Collectors.toList());
            }

            if (models.isEmpty()) {
                models.add("No models found");
            }
        } catch (IOException e) {
            Chat_llm_translation.LOGGER.error("[ChatLLM] Failed to list model files", e);
            models.add("Error loading models");
        }

        return models;
    }

    /**
     * モデル名からフルパスを取得
     *
     * @param modelName モデルファイル名
     * @return モデルファイルのフルパス
     */
    public static Path getModelPath(String modelName) {
        Path modelsDir = ModelDownloader.getModelPath().getParent();
        return modelsDir.resolve(modelName);
    }
}
