package jp.chat_llm_translation.chat;

import jp.chat_llm_translation.config.ModConfig;
import jp.chat_llm_translation.llm.LLMClient.ChatMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * チャット履歴を管理するクラス
 * LLMのコンテキストとして使用するため、過去のメッセージを保持します
 */
public class ChatHistory {
    /**
     * チャットメッセージエントリ
     */
    public static class ChatEntry {
        public final String playerName;
        public final String originalMessage;
        public final String translatedMessage;
        public final Instant timestamp;
        public final boolean isOutgoing; // true: 送信, false: 受信

        public ChatEntry(String playerName, String originalMessage, String translatedMessage, boolean isOutgoing) {
            this.playerName = playerName;
            this.originalMessage = originalMessage;
            this.translatedMessage = translatedMessage;
            this.timestamp = Instant.now();
            this.isOutgoing = isOutgoing;
        }
    }

    private final LinkedList<ChatEntry> history;
    private final ModConfig config;
    private final int maxSize;

    /**
     * コンストラクタ
     */
    public ChatHistory() {
        this.config = ModConfig.getInstance();
        this.maxSize = config.chatHistorySize;
        this.history = new LinkedList<>();
    }

    /**
     * チャットメッセージを履歴に追加
     *
     * @param playerName プレイヤー名
     * @param originalMessage 元のメッセージ
     * @param translatedMessage 翻訳されたメッセージ
     * @param isOutgoing 送信メッセージかどうか
     */
    public synchronized void addMessage(String playerName, String originalMessage, String translatedMessage, boolean isOutgoing) {
        ChatEntry entry = new ChatEntry(playerName, originalMessage, translatedMessage, isOutgoing);
        history.addLast(entry);

        // 履歴サイズが上限を超えた場合、古いメッセージを削除
        while (history.size() > maxSize) {
            history.removeFirst();
        }
    }

    /**
     * 履歴からLLMコンテキスト用のメッセージリストを取得
     * 最近のN件のメッセージを返す
     *
     * @param count 取得するメッセージ数
     * @return ChatMessageのリスト
     */
    public synchronized List<ChatMessage> getContextMessages(int count) {
        List<ChatMessage> messages = new ArrayList<>();

        // 履歴の後ろからcount件取得
        int startIndex = Math.max(0, history.size() - count);
        List<ChatEntry> recentEntries = history.subList(startIndex, history.size());

        for (ChatEntry entry : recentEntries) {
            // プレイヤー名とメッセージを含むコンテキストを作成
            String contextContent = String.format("[%s]: %s", entry.playerName, entry.originalMessage);

            // 受信メッセージはassistant（翻訳済み）として、送信メッセージはuserとして追加
            if (entry.isOutgoing) {
                messages.add(new ChatMessage("user", contextContent));
            } else {
                // 翻訳済みメッセージも含める
                messages.add(new ChatMessage("user", contextContent));
                if (entry.translatedMessage != null && !entry.translatedMessage.equals(entry.originalMessage)) {
                    messages.add(new ChatMessage("assistant", entry.translatedMessage));
                }
            }
        }

        return messages;
    }

    /**
     * 全履歴を取得
     *
     * @return 全チャットエントリのリスト
     */
    public synchronized List<ChatEntry> getAllHistory() {
        return new ArrayList<>(history);
    }

    /**
     * 履歴をクリア
     */
    public synchronized void clear() {
        history.clear();
    }

    /**
     * 履歴のサイズを取得
     *
     * @return 現在の履歴サイズ
     */
    public synchronized int size() {
        return history.size();
    }

    /**
     * 最新のN件のエントリを取得
     *
     * @param count 取得する件数
     * @return ChatEntryのリスト
     */
    public synchronized List<ChatEntry> getRecentEntries(int count) {
        int startIndex = Math.max(0, history.size() - count);
        return new ArrayList<>(history.subList(startIndex, history.size()));
    }

    /**
     * 特定のプレイヤーのメッセージのみを取得
     *
     * @param playerName プレイヤー名
     * @return ChatEntryのリスト
     */
    public synchronized List<ChatEntry> getMessagesByPlayer(String playerName) {
        List<ChatEntry> result = new ArrayList<>();
        for (ChatEntry entry : history) {
            if (entry.playerName.equals(playerName)) {
                result.add(entry);
            }
        }
        return result;
    }
}
