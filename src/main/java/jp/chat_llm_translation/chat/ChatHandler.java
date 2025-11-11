package jp.chat_llm_translation.chat;

import jp.chat_llm_translation.config.ModConfig;
import jp.chat_llm_translation.llm.LLMClient;
import jp.chat_llm_translation.rag.RAGStorage;
import jp.chat_llm_translation.storage.ServerStorageManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * チャットメッセージの翻訳を処理するハンドラ
 */
public class ChatHandler {
    private final LLMClient llmClient;
    private final ServerStorageManager storageManager;
    private final ModConfig config;
    private final ConcurrentHashMap<String, String> translationCache;

    /**
     * コンストラクタ
     */
    public ChatHandler() {
        this.llmClient = new LLMClient();
        this.storageManager = new ServerStorageManager();
        this.config = ModConfig.getInstance();
        this.translationCache = new ConcurrentHashMap<>();
    }

    /**
     * 受信チャットメッセージを処理（非同期）
     *
     * @param playerName プレイヤー名
     * @param message メッセージ内容
     * @return 翻訳されたメッセージのCompletableFuture
     */
    public CompletableFuture<String> handleIncomingMessage(String playerName, String message) {
        System.out.println("[ChatLLM] handleIncomingMessage: player=" + playerName + ", message=" + message);
        System.out.println("[ChatLLM] translationEnabled=" + config.translationEnabled + ", autoTranslateIncoming=" + config.autoTranslateIncoming);

        if (!config.translationEnabled || !config.autoTranslateIncoming) {
            System.out.println("[ChatLLM] Translation disabled, returning original");
            return CompletableFuture.completedFuture(message);
        }

        // サーバーストレージを取得
        ChatHistory chatHistory = storageManager.getCurrentChatHistory();
        RAGStorage ragStorage = storageManager.getCurrentRAGStorage();

        if (chatHistory == null || ragStorage == null) {
            System.out.println("[ChatLLM] No server storage available, returning original");
            return CompletableFuture.completedFuture(message);
        }

        // キャッシュをチェック
        String cached = translationCache.get(message);
        if (cached != null) {
            System.out.println("[ChatLLM] Cache hit: " + message + " -> " + cached);
            chatHistory.addMessage(playerName, message, cached, false);
            return CompletableFuture.completedFuture(cached);
        }

        System.out.println("[ChatLLM] Cache miss, checking RAG storage");

        // RAGストレージで完全一致を検索
        RAGStorage.RAGEntry exactMatch = ragStorage.getExactMatch(message);
        if (exactMatch != null) {
            System.out.println("[ChatLLM] RAG exact match: " + message + " -> " + exactMatch.translatedText);
            String translated = exactMatch.translatedText;
            translationCache.put(message, translated);
            chatHistory.addMessage(playerName, message, translated, false);
            return CompletableFuture.completedFuture(translated);
        }

        System.out.println("[ChatLLM] No cache/RAG match, translating with LLM");
        // LLMで翻訳
        return translateWithLLM(playerName, message, false);
    }

    /**
     * 送信チャットメッセージを処理（非同期）
     *
     * @param playerName プレイヤー名
     * @param message メッセージ内容
     * @return 翻訳されたメッセージのCompletableFuture
     */
    public CompletableFuture<String> handleOutgoingMessage(String playerName, String message) {
        if (!config.translationEnabled || !config.autoTranslateOutgoing) {
            return CompletableFuture.completedFuture(message);
        }

        // サーバーストレージを取得
        ChatHistory chatHistory = storageManager.getCurrentChatHistory();
        RAGStorage ragStorage = storageManager.getCurrentRAGStorage();

        if (chatHistory == null || ragStorage == null) {
            return CompletableFuture.completedFuture(message);
        }

        // キャッシュをチェック
        String cached = translationCache.get(message);
        if (cached != null) {
            chatHistory.addMessage(playerName, message, cached, true);
            return CompletableFuture.completedFuture(cached);
        }

        // RAGストレージで完全一致を検索
        RAGStorage.RAGEntry exactMatch = ragStorage.getExactMatch(message);
        if (exactMatch != null) {
            String translated = exactMatch.translatedText;
            translationCache.put(message, translated);
            chatHistory.addMessage(playerName, message, translated, true);
            return CompletableFuture.completedFuture(translated);
        }

        // LLMで翻訳
        return translateWithLLM(playerName, message, true);
    }

    /**
     * LLMを使用して翻訳
     *
     * @param playerName プレイヤー名
     * @param message メッセージ
     * @param isOutgoing 送信メッセージかどうか
     * @return 翻訳されたメッセージのCompletableFuture
     */
    private CompletableFuture<String> translateWithLLM(String playerName, String message, boolean isOutgoing) {
        System.out.println("[ChatLLM] translateWithLLM: message=" + message + ", isOutgoing=" + isOutgoing);

        // サーバーストレージを取得
        ChatHistory chatHistory = storageManager.getCurrentChatHistory();
        RAGStorage ragStorage = storageManager.getCurrentRAGStorage();

        if (chatHistory == null || ragStorage == null) {
            return CompletableFuture.completedFuture(message);
        }

        // 送信メッセージの場合はコンテキストなしで翻訳（会話と誤解されないように）
        // 受信メッセージの場合のみコンテキストを使用
        var contextMessages = isOutgoing ? null : chatHistory.getContextMessages(3);

        // 送信メッセージと受信メッセージで異なる言語設定を使用
        String targetLanguage = isOutgoing ? config.outgoingTargetLanguage : config.targetLanguage;

        System.out.println("[ChatLLM] Calling LLM API with targetLanguage=" + targetLanguage);
        // LLMで翻訳
        return llmClient.translateAsync(message, contextMessages, targetLanguage)
                .thenApply(translated -> {
                    System.out.println("[ChatLLM] LLM returned: " + message + " -> " + translated);

                    // 翻訳結果をキャッシュに保存
                    translationCache.put(message, translated);

                    // チャット履歴に追加
                    chatHistory.addMessage(playerName, message, translated, isOutgoing);

                    // RAGストレージに追加（プレイヤー名をコンテキストとして）
                    ragStorage.addOrUpdate(message, translated, playerName);

                    System.out.println("[ChatLLM] Translation complete: " + message + " -> " + translated);

                    return translated;
                })
                .exceptionally(ex -> {
                    if (config.debugMode) {
                        System.err.println("[ChatLLM] Translation error: " + ex.getMessage());
                    }
                    // エラー時は元のメッセージを返す
                    return message;
                });
    }

    /**
     * クライアントのチャットに翻訳メッセージを表示
     *
     * @param originalMessage 元のメッセージ
     * @param translatedMessage 翻訳されたメッセージ
     */
    public void displayTranslation(String originalMessage, String translatedMessage) {
        // 元のメッセージと翻訳が同じ場合は表示しない
        if (originalMessage.equals(translatedMessage)) {
            return;
        }

        // 翻訳が空の場合は表示しない
        if (translatedMessage == null || translatedMessage.isBlank()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            // 翻訳結果を表示（原文付き）
            // 形式: [翻訳] 翻訳文 (原文: 元の文章)
            String formattedMessage = config.translationLabelColor + "[翻訳] " +
                                    config.translationTextColor + translatedMessage +
                                    " " + config.originalLabelColor + "(原文: " +
                                    config.originalTextColor + originalMessage +
                                    config.originalLabelColor + ")";

            client.player.sendMessage(
                    Text.literal(formattedMessage),
                    false
            );
        }
    }

    /**
     * 手動翻訳コマンド
     * ユーザーが手動で翻訳をトリガーする場合に使用
     *
     * @param message 翻訳するメッセージ
     */
    public void manualTranslate(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        String playerName = client.player.getName().getString();

        handleIncomingMessage(playerName, message)
                .thenAccept(translated -> {
                    displayTranslation(message, translated);
                });
    }

    /**
     * キャッシュをクリア
     */
    public void clearCache() {
        translationCache.clear();
        if (config.debugMode) {
            System.out.println("[ChatLLM] Translation cache cleared");
        }
    }

    /**
     * チャット履歴をクリア
     */
    public void clearHistory() {
        ChatHistory chatHistory = storageManager.getCurrentChatHistory();
        if (chatHistory != null) {
            chatHistory.clear();
            if (config.debugMode) {
                System.out.println("[ChatLLM] Chat history cleared");
            }
        }
    }

    /**
     * RAGストレージをクリア
     */
    public void clearRAG() {
        RAGStorage ragStorage = storageManager.getCurrentRAGStorage();
        if (ragStorage != null) {
            ragStorage.clear();
            if (config.debugMode) {
                System.out.println("[ChatLLM] RAG storage cleared");
            }
        }
    }

    /**
     * 全データをクリア
     */
    public void clearAll() {
        clearCache();
        clearHistory();
        clearRAG();
    }

    /**
     * 統計情報を取得
     */
    public String getStats() {
        ChatHistory chatHistory = storageManager.getCurrentChatHistory();
        RAGStorage ragStorage = storageManager.getCurrentRAGStorage();

        int historySize = (chatHistory != null) ? chatHistory.size() : 0;
        int ragSize = (ragStorage != null) ? ragStorage.size() : 0;

        return String.format(
                "Cache: %d, History: %d, RAG: %d",
                translationCache.size(),
                historySize,
                ragSize
        );
    }

    /**
     * LLMサーバーへの接続テスト
     *
     * @return 接続成功の場合true
     */
    public boolean testConnection() {
        return llmClient.testConnection();
    }

    /**
     * RAGストレージを保存
     */
    public void saveRAG() {
        storageManager.saveAll();
    }

    /**
     * サーバーに参加した時の処理
     *
     * @param serverAddress サーバーアドレス（null の場合はシングルプレイ）
     */
    public void onServerJoin(String serverAddress) {
        storageManager.onServerJoin(serverAddress);
    }

    /**
     * サーバーから切断した時の処理
     */
    public void onServerLeave() {
        storageManager.onServerLeave();
    }

    // ゲッター
    public ChatHistory getChatHistory() {
        return storageManager.getCurrentChatHistory();
    }

    public RAGStorage getRAGStorage() {
        return storageManager.getCurrentRAGStorage();
    }

    public ServerStorageManager getStorageManager() {
        return storageManager;
    }
}
