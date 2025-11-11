package jp.chat_llm_translation.downloader;

import jp.chat_llm_translation.Chat_llm_translation;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * モデルとllama-serverの自動ダウンロードを管理するクラス
 */
public class ModelDownloader {
    // --- Public Static State for HUD ---
    public static volatile DownloadProgress currentProgress = null;

    // --- Constants ---
    private static final String GITHUB_RELEASE_BASE = "https://github.com/Rangesa/Chat-LLM-Translation/releases/download/v1.0.0/";
    private static final String MODEL_FILENAME = "gemma-3-4b-q4.gguf";
    private static final String MODEL_PART_PREFIX = "gemma-3-4b-q4.gguf.part";
    private static final int MODEL_PARTS_COUNT = 5;
    private static final String LLAMA_SERVER_WINDOWS = "llama-server-windows.zip";
    private static final String LLAMA_SERVER_LINUX = "llama-server-linux";
    private static final String LLAMA_SERVER_MACOS = "llama-server-macos";

    private static final Path MOD_DIR = FabricLoader.getInstance().getGameDir().resolve("chat_llm_translation");
    private static final Path MODELS_DIR = MOD_DIR.resolve("models");
    private static final Path BIN_DIR = MOD_DIR.resolve("bin");

    /**
     * 初回起動時に必要なファイルが存在するか確認し、なければダウンロード（UI非依存）
     *
     * @return ダウンロード完了を示すCompletableFuture
     */
    public static CompletableFuture<Void> ensureFilesExist() {
        return CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(MODELS_DIR);
                Files.createDirectories(BIN_DIR);

                Path modelPath = MODELS_DIR.resolve(MODEL_FILENAME);
                Path llamaServerPath = getBinPath();

                boolean needsModelDownload = !Files.exists(modelPath);
                boolean needsServerDownload = !Files.exists(llamaServerPath);

                if (!needsModelDownload && !needsServerDownload) {
                    Chat_llm_translation.LOGGER.info("[ChatLLM] All files exist, skipping download");
                    return;
                }

                Chat_llm_translation.LOGGER.info("[ChatLLM] Starting automatic background download...");

                if (needsModelDownload) {
                    downloadModelPartsInParallel();
                }

                if (needsServerDownload) {
                    downloadLlamaServer();
                }

                currentProgress = new DownloadProgress("Complete", 1.0, "Download complete!", true);
                Chat_llm_translation.LOGGER.info("[ChatLLM] All downloads completed successfully");

            } catch (Exception e) {
                Chat_llm_translation.LOGGER.error("[ChatLLM] Download failed", e);
                currentProgress = new DownloadProgress("Error", -1, "Download failed! Check logs.", true);
            }
        });
    }

    private static void downloadModelPartsInParallel() throws IOException {
        Chat_llm_translation.LOGGER.info("[ChatLLM] Downloading model in {} parts: {}", MODEL_PARTS_COUNT, MODEL_FILENAME);
        currentProgress = new DownloadProgress("Model", 0.0, "Starting model download...", false);

        ExecutorService executor = Executors.newFixedThreadPool(MODEL_PARTS_COUNT);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        double[] progresses = new double[MODEL_PARTS_COUNT];
        Object progressLock = new Object();
        Path[] parts = new Path[MODEL_PARTS_COUNT];

        for (int i = 0; i < MODEL_PARTS_COUNT; i++) {
            final int partIndex = i;
            String partFilename = MODEL_PART_PREFIX + (partIndex + 1);
            Path partPath = MODELS_DIR.resolve(partFilename);
            parts[partIndex] = partPath;
            String downloadUrl = GITHUB_RELEASE_BASE + partFilename;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    downloadFile(downloadUrl, partPath, (partProgress) -> {
                        synchronized (progressLock) {
                            progresses[partIndex] = partProgress;
                            double totalProgress = Arrays.stream(progresses).sum() / MODEL_PARTS_COUNT;
                            long completedCount = Arrays.stream(progresses).filter(p -> p >= 1.0).count();
                            currentProgress = new DownloadProgress("Model", totalProgress,
                                    String.format("Downloading... (%d/%d parts)", completedCount, MODEL_PARTS_COUNT), false);
                        }
                    });
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }, executor);
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            throw new IOException("Failed to download one or more model parts", e);
        } finally {
            executor.shutdown();
        }

        Chat_llm_translation.LOGGER.info("[ChatLLM] All model parts downloaded. Merging...");
        currentProgress = new DownloadProgress("Model", 0.98, "Merging model parts...", false);
        mergeFiles(parts, MODELS_DIR.resolve(MODEL_FILENAME));

        for (Path part : parts) {
            Files.deleteIfExists(part);
        }
        Chat_llm_translation.LOGGER.info("[ChatLLM] Model download and merge complete");
    }

    private static void downloadLlamaServer() throws IOException {
        String serverFilename = getLlamaServerFilename();
        Chat_llm_translation.LOGGER.info("[ChatLLM] Downloading llama-server: {}", serverFilename);
        currentProgress = new DownloadProgress("LLM Server", 0.0, "Downloading LLM server...", false);

        Path downloadPath;
        if (isWindows()) {
            // Windows: Download zip file
            downloadPath = BIN_DIR.resolve(LLAMA_SERVER_WINDOWS);
        } else {
            // Linux/Mac: Download executable directly
            downloadPath = getBinPath();
        }

        downloadFile(
            GITHUB_RELEASE_BASE + serverFilename,
            downloadPath,
            progress -> currentProgress = new DownloadProgress("LLM Server", progress, "Downloading LLM server...", false)
        );

        // Extract zip for Windows
        if (isWindows()) {
            Chat_llm_translation.LOGGER.info("[ChatLLM] Extracting llama-server...");
            currentProgress = new DownloadProgress("LLM Server", 0.95, "Extracting...", false);
            extractZip(downloadPath, BIN_DIR);
            Files.deleteIfExists(downloadPath); // Clean up zip file
        }

        // Set executable permissions for Linux/Mac
        if (!isWindows()) {
            getBinPath().toFile().setExecutable(true, false);
        }

        Chat_llm_translation.LOGGER.info("[ChatLLM] llama-server download complete");
    }

    private static void mergeFiles(Path[] parts, Path destination) throws IOException {
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
            for (Path part : parts) {
                try (InputStream in = new BufferedInputStream(Files.newInputStream(part))) {
                    in.transferTo(out);
                }
            }
        }
    }

    /**
     * Zipファイルを解凍する
     *
     * @param zipPath 解凍するzipファイルのパス
     * @param destDir 解凍先ディレクトリ
     * @throws IOException 解凍中にエラーが発生した場合
     */
    private static void extractZip(Path zipPath, Path destDir) throws IOException {
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                new BufferedInputStream(Files.newInputStream(zipPath)))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path filePath = destDir.resolve(entry.getName());

                // ディレクトリトラバーサル対策
                if (!filePath.normalize().startsWith(destDir.normalize())) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(filePath))) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void downloadFile(String urlString, Path destination, Consumer<Double> progressCallback) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(30000);
        connection.setReadTimeout(30000);

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) {
                String newUrl = connection.getHeaderField("Location");
                Chat_llm_translation.LOGGER.warn("[ChatLLM] Request redirected to: {}. Retrying...", newUrl);
                connection.disconnect();
                downloadFile(newUrl, destination, progressCallback); // Retry with new URL
                return;
            } else {
                throw new IOException("Server returned non-OK status: " + responseCode);
            }
        }

        long fileSize = connection.getContentLengthLong();
        try (InputStream in = new BufferedInputStream(connection.getInputStream());
             OutputStream out = new BufferedOutputStream(Files.newOutputStream(destination))) {
            byte[] buffer = new byte[8192];
            long totalBytesRead = 0;
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                if (fileSize > 0) {
                    progressCallback.accept((double) totalBytesRead / fileSize);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    // --- Helper Methods ---
    private static Path getBinPath() {
        String filename = isWindows() ? "llama-server.exe" : "llama-server";
        return BIN_DIR.resolve(filename);
    }

    private static String getLlamaServerFilename() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return LLAMA_SERVER_WINDOWS;
        if (os.contains("mac")) return LLAMA_SERVER_MACOS;
        return LLAMA_SERVER_LINUX;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    public static Path getModelPath() {
        return MODELS_DIR.resolve(MODEL_FILENAME);
    }

    public static Path getLlamaServerPath() {
        return getBinPath();
    }

    // --- Public Data Class ---
    public static class DownloadProgress {
        public final String item;
        public final double progress;
        public final String message;
        public final boolean isFinished; // Flag to indicate the process is done (success or error)

        public DownloadProgress(String item, double progress, String message, boolean isFinished) {
            this.item = item;
            this.progress = progress;
            this.message = message;
            this.isFinished = isFinished;
        }

        public int getProgressPercent() {
            return (int) (progress * 100);
        }
    }
}
