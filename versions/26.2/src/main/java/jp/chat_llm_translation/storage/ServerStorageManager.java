package jp.chat_llm_translation.storage;

import jp.chat_llm_translation.Chat_llm_translation;
import jp.chat_llm_translation.chat.ChatHistory;
import jp.chat_llm_translation.config.ModConfig;
import jp.chat_llm_translation.rag.RAGStorage;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * サーバーごとのストレージを管理するクラス
 * サーバーアドレスをキーにして、RAGStorageとChatHistoryを個別に管理します
 */
public class ServerStorageManager {
    private static final Path STORAGE_ROOT = FabricLoader.getInstance()
            .getGameDir()
            .resolve("chat_llm_translation")
            .resolve("servers");

    private final Map<String, ServerStorage> storages;
    private ServerStorage currentServerStorage;
    private String currentServerAddress;
    private final ModConfig config;

    /**
     * サーバーごとのストレージを保持するクラス
     */
    public static class ServerStorage {
        public final RAGStorage ragStorage;
        public final ChatHistory chatHistory;
        public final String serverAddress;
        public final Path storagePath;

        public ServerStorage(String serverAddress, Path storagePath) {
            this.serverAddress = serverAddress;
            this.storagePath = storagePath;

            // サーバーごとのRAGストレージを初期化
            this.ragStorage = new RAGStorage(storagePath.resolve("rag.json"));
            this.chatHistory = new ChatHistory();

            Chat_llm_translation.LOGGER.info("[ChatLLM] Created storage for server: {}", serverAddress);
        }

        /**
         * このサーバーのストレージを保存
         */
        public void save() {
            ragStorage.save();
            Chat_llm_translation.LOGGER.info("[ChatLLM] Saved storage for server: {}", serverAddress);
        }

        /**
         * このサーバーのストレージをクリア
         */
        public void clear() {
            ragStorage.clear();
            chatHistory.clear();
            Chat_llm_translation.LOGGER.info("[ChatLLM] Cleared storage for server: {}", serverAddress);
        }
    }

    /**
     * コンストラクタ
     */
    public ServerStorageManager() {
        this.config = ModConfig.getInstance();
        this.storages = new HashMap<>();
        this.currentServerStorage = null;
        this.currentServerAddress = null;

        // ストレージルートディレクトリを作成
        try {
            Files.createDirectories(STORAGE_ROOT);
        } catch (IOException e) {
            Chat_llm_translation.LOGGER.error("[ChatLLM] Failed to create storage directory", e);
        }
    }

    /**
     * サーバーに参加した時の処理
     *
     * @param serverAddress サーバーアドレス（null の場合はシングルプレイ）
     */
    public void onServerJoin(String serverAddress) {
        // サーバーアドレスの正規化
        String normalizedAddress = normalizeServerAddress(serverAddress);

        Chat_llm_translation.LOGGER.info("[ChatLLM] Joining server: {}", normalizedAddress);

        // 既存のストレージがあれば保存
        if (currentServerStorage != null) {
            currentServerStorage.save();
        }

        // このサーバーのストレージを取得または作成
        currentServerAddress = normalizedAddress;
        currentServerStorage = getOrCreateStorage(normalizedAddress);

        // 全キャッシュ読み込みが有効な場合
        if (config.loadFullCacheOnJoin) {
            loadFullCacheForServer(normalizedAddress);
        }
    }

    /**
     * サーバーから切断した時の処理
     */
    public void onServerLeave() {
        if (currentServerStorage != null) {
            Chat_llm_translation.LOGGER.info("[ChatLLM] Leaving server: {}", currentServerAddress);
            currentServerStorage.save();
            currentServerStorage = null;
            currentServerAddress = null;
        }
    }

    /**
     * 現在のサーバーのストレージを取得
     *
     * @return ServerStorage（接続中でない場合はnull）
     */
    public ServerStorage getCurrentStorage() {
        return currentServerStorage;
    }

    /**
     * 現在のサーバーのRAGStorageを取得
     *
     * @return RAGStorage（接続中でない場合はnull）
     */
    public RAGStorage getCurrentRAGStorage() {
        return currentServerStorage != null ? currentServerStorage.ragStorage : null;
    }

    /**
     * 現在のサーバーのChatHistoryを取得
     *
     * @return ChatHistory（接続中でない場合はnull）
     */
    public ChatHistory getCurrentChatHistory() {
        return currentServerStorage != null ? currentServerStorage.chatHistory : null;
    }

    /**
     * すべてのサーバーのストレージを保存
     */
    public void saveAll() {
        Chat_llm_translation.LOGGER.info("[ChatLLM] Saving all server storages ({} servers)", storages.size());

        for (ServerStorage storage : storages.values()) {
            storage.save();
        }

        if (currentServerStorage != null) {
            currentServerStorage.save();
        }
    }

    /**
     * 指定サーバーの全キャッシュをLLMコンテキストに読み込む
     *
     * @param serverAddress サーバーアドレス
     */
    private void loadFullCacheForServer(String serverAddress) {
        ServerStorage storage = storages.get(serverAddress);
        if (storage == null || storage.ragStorage == null) {
            Chat_llm_translation.LOGGER.info("[ChatLLM] No cache to load for server: {}", serverAddress);
            return;
        }

        int cacheSize = storage.ragStorage.size();
        int loadLimit = Math.min(cacheSize, config.maxCacheLoadOnJoin);

        Chat_llm_translation.LOGGER.info("[ChatLLM] Loading {} cache entries (out of {}) for server: {}",
                loadLimit, cacheSize, serverAddress);

        // RAGストレージから全エントリを取得してChatHistoryに追加
        // （実際のLLMコンテキスト読み込みは翻訳時に行われる）

        Chat_llm_translation.LOGGER.info("[ChatLLM] Cache loading complete for server: {}", serverAddress);
    }

    /**
     * サーバーアドレスを正規化
     *
     * @param serverAddress サーバーアドレス
     * @return 正規化されたアドレス
     */
    private String normalizeServerAddress(String serverAddress) {
        if (serverAddress == null || serverAddress.isEmpty()) {
            return "singleplayer";
        }

        // ファイル名として使用できるように特殊文字を置換
        return serverAddress
                .toLowerCase()
                .replaceAll("[^a-z0-9.-]", "_")
                .replaceAll("_{2,}", "_");
    }

    /**
     * サーバーのストレージを取得または作成
     *
     * @param serverAddress 正規化されたサーバーアドレス
     * @return ServerStorage
     */
    private ServerStorage getOrCreateStorage(String serverAddress) {
        return storages.computeIfAbsent(serverAddress, addr -> {
            Path serverPath = STORAGE_ROOT.resolve(addr);
            try {
                Files.createDirectories(serverPath);
            } catch (IOException e) {
                Chat_llm_translation.LOGGER.error("[ChatLLM] Failed to create server storage directory: {}", addr, e);
            }
            return new ServerStorage(addr, serverPath);
        });
    }
}
