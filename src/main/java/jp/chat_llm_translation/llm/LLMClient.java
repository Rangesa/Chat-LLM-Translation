package jp.chat_llm_translation.llm;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import jp.chat_llm_translation.config.ModConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * llama.cpp サーバーと通信してLLM推論を実行するクライアント
 * OpenAI互換APIを使用
 */
public class LLMClient {
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;
    private final ModConfig config;

    /**
     * コンストラクタ
     */
    public LLMClient() {
        this.config = ModConfig.getInstance();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.requestTimeout))
                .build();
    }

    /**
     * チャットメッセージ構造
     */
    public static class ChatMessage {
        public final String role;    // "system", "user", "assistant"
        public final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /**
     * llama.cppサーバーに翻訳リクエストを送信（非同期）
     *
     * @param text 翻訳するテキスト
     * @return 翻訳されたテキストのCompletableFuture
     */
    public CompletableFuture<String> translateAsync(String text) {
        return translateAsync(text, null);
    }

    /**
     * llama.cppサーバーに翻訳リクエストを送信（非同期、コンテキスト付き）
     *
     * @param text 翻訳するテキスト
     * @param contextMessages コンテキストとなる過去のメッセージ（nullの場合はコンテキストなし）
     * @return 翻訳されたテキストのCompletableFuture
     */
    public CompletableFuture<String> translateAsync(String text, List<ChatMessage> contextMessages) {
        return translateAsync(text, contextMessages, null);
    }

    /**
     * llama.cppサーバーに翻訳リクエストを送信（非同期、言語指定可能）
     *
     * @param text 翻訳するテキスト
     * @param contextMessages コンテキストとなる過去のメッセージ（nullの場合はコンテキストなし）
     * @param targetLanguage 翻訳先言語（nullの場合は設定ファイルの言語を使用）
     * @return 翻訳されたテキストのCompletableFuture
     */
    public CompletableFuture<String> translateAsync(String text, List<ChatMessage> contextMessages, String targetLanguage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return translate(text, contextMessages, targetLanguage);
            } catch (Exception e) {
                if (config.debugMode) {
                    System.err.println("[ChatLLM] Translation failed: " + e.getMessage());
                    e.printStackTrace();
                }
                // 翻訳失敗時は元のテキストを返す
                return text;
            }
        });
    }

    /**
     * llama.cppサーバーに翻訳リクエストを送信（同期）
     *
     * @param text 翻訳するテキスト
     * @param contextMessages コンテキストとなる過去のメッセージ（nullの場合はコンテキストなし）
     * @return 翻訳されたテキスト
     * @throws IOException 通信エラー
     * @throws InterruptedException スレッド中断
     */
    public String translate(String text, List<ChatMessage> contextMessages) throws IOException, InterruptedException {
        return translate(text, contextMessages, null);
    }

    /**
     * llama.cppサーバーに翻訳リクエストを送信（同期、言語指定可能）
     *
     * @param text 翻訳するテキスト
     * @param contextMessages コンテキストとなる過去のメッセージ（nullの場合はコンテキストなし）
     * @param targetLanguage 翻訳先言語（nullの場合は設定ファイルの言語を使用）
     * @return 翻訳されたテキスト
     * @throws IOException 通信エラー
     * @throws InterruptedException スレッド中断
     */
    public String translate(String text, List<ChatMessage> contextMessages, String targetLanguage) throws IOException, InterruptedException {
        // メッセージリストを構築
        List<ChatMessage> messages = new ArrayList<>();

        // システムプロンプトを追加（言語指定がある場合はそれを使用）
        String systemPrompt;
        if (targetLanguage != null && !targetLanguage.isEmpty()) {
            systemPrompt = String.format(config.systemPrompt, targetLanguage, targetLanguage);
        } else {
            systemPrompt = config.getFormattedSystemPrompt();
        }
        messages.add(new ChatMessage("system", systemPrompt));

        // コンテキストメッセージを追加（存在する場合）
        if (contextMessages != null && !contextMessages.isEmpty()) {
            messages.addAll(contextMessages);
        }

        // 翻訳対象のテキストを追加
        messages.add(new ChatMessage("user", text));

        // リクエストボディを構築
        JsonObject requestBody = new JsonObject();

        // メッセージ配列を構築
        JsonArray messagesArray = new JsonArray();
        for (ChatMessage msg : messages) {
            JsonObject messageObj = new JsonObject();
            messageObj.addProperty("role", msg.role);
            messageObj.addProperty("content", msg.content);
            messagesArray.add(messageObj);
        }

        requestBody.add("messages", messagesArray);
        requestBody.addProperty("max_tokens", config.maxTokens);
        requestBody.addProperty("temperature", config.temperature);
        requestBody.addProperty("top_p", config.topP);
        requestBody.addProperty("stream", false);

        if (config.debugMode) {
            System.out.println("[ChatLLM] Request: " + GSON.toJson(requestBody));
        }

        // HTTPリクエストを構築
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.llmServerUrl + "/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(config.requestTimeout))
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(requestBody)))
                .build();

        // HTTPリクエストを送信
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // レスポンスをパース
        if (response.statusCode() == 200) {
            JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);

            if (config.debugMode) {
                System.out.println("[ChatLLM] Response: " + response.body());
            }

            // OpenAI互換レスポンスから翻訳テキストを抽出
            if (responseJson.has("choices") && responseJson.getAsJsonArray("choices").size() > 0) {
                JsonObject choice = responseJson.getAsJsonArray("choices").get(0).getAsJsonObject();
                if (choice.has("message")) {
                    JsonObject message = choice.getAsJsonObject("message");
                    if (message.has("content")) {
                        return message.get("content").getAsString().trim();
                    }
                }
            }

            throw new IOException("Invalid response format from LLM server");
        } else {
            throw new IOException("LLM server returned error: " + response.statusCode() + " - " + response.body());
        }
    }

    /**
     * サーバーの接続テスト
     *
     * @return 接続成功の場合true
     */
    public boolean testConnection() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.llmServerUrl + "/health"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            if (config.debugMode) {
                System.err.println("[ChatLLM] Connection test failed: " + e.getMessage());
            }
            return false;
        }
    }
}
