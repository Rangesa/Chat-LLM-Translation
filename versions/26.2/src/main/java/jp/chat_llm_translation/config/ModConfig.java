package jp.chat_llm_translation.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MODの設定を管理するクラス
 * JSON形式で設定ファイルを保存・読み込みします
 */
public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("chat_llm_translation.json");

    private static ModConfig INSTANCE;

    // ============================================================
    // 設定項目
    // ============================================================

    /**
     * LLMサーバーのURL
     * デフォルト: http://localhost:8080
     */
    public String llmServerUrl = "http://localhost:8080";

    /**
     * オンラインAPI使用フラグ
     * trueの場合、onlineApiUrlとonlineApiKeyを使用
     */
    public boolean useOnlineApi = false;

    /**
     * オンラインAPIのURL
     * 例: "https://api.openai.com/v1/chat/completions"
     */
    public String onlineApiUrl = "";

    /**
     * オンラインAPIのAPIキー
     */
    public String onlineApiKey = "";

    /**
     * 翻訳機能の有効/無効
     */
    public boolean translationEnabled = true;

    /**
     * 受信チャットの自動翻訳
     */
    public boolean autoTranslateIncoming = true;

    /**
     * 送信チャットの自動翻訳
     */
    public boolean autoTranslateOutgoing = false;

    /**
     * 翻訳先言語（受信メッセージ用）
     * 例: "Japanese", "English", "Chinese", "Korean"
     */
    public String targetLanguage = "Japanese";

    /**
     * 翻訳先言語（送信メッセージ用）
     * 例: "English", "Japanese", "Chinese", "Korean"
     */
    public String outgoingTargetLanguage = "English";

    /**
     * システムプロンプト
     * LLMに渡される翻訳の指示
     */
    public String systemPrompt = """
            You are a pure translation machine. Translate to %s ONLY.

            ABSOLUTE RULES - NO EXCEPTIONS:
            1. OUTPUT = TRANSLATION ONLY (nothing else)
            2. NO greetings, NO responses, NO conversations
            3. NO explanations, NO comments, NO extra words
            4. NO "Here is", NO "The translation is", NO formatting
            5. If already in %s, output unchanged
            6. Preserve emojis, special characters, and tone exactly

            EXAMPLES:
            Input: "Hello, how are you?"
            Output: こんにちは、元気ですか？

            Input: "Thank you!"
            Output: ありがとうございます！

            Input: "すでに日本語です"
            Output: すでに日本語です

            Translate now:
            """;

    /**
     * チャット履歴の保存数
     * LLMに渡すコンテキストとして使用
     */
    public int chatHistorySize = 50;

    /**
     * RAG機能の有効/無効
     */
    public boolean ragEnabled = true;

    /**
     * RAGストレージの最大サイズ（エントリ数）
     */
    public int ragMaxEntries = 1000;

    /**
     * LLMリクエストのタイムアウト（ミリ秒）
     */
    public int requestTimeout = 10000;

    /**
     * 送信メッセージ翻訳の最大待機時間（ミリ秒）
     * この時間を超えたら元のメッセージを送信してゲームのブロックを防ぐ
     */
    public int outgoingTranslationTimeout = 5000;

    /**
     * 最大トークン数
     */
    public int maxTokens = 256;

    /**
     * 温度パラメータ（0.0 - 2.0）
     * 低いほど決定論的、高いほど創造的
     */
    public double temperature = 0.3;

    /**
     * Top-P サンプリング（0.0 - 1.0）
     */
    public double topP = 0.9;

    /**
     * デバッグモード
     * trueの場合、詳細なログを出力
     */
    public boolean debugMode = false;

    /**
     * サーバー参加時に全キャッシュを読み込むかどうか
     * trueの場合、そのサーバーの過去の全チャット履歴をLLMコンテキストに読み込む
     * 注意: 大量のキャッシュがある場合、初回翻訳が重くなる可能性があります
     */
    public boolean loadFullCacheOnJoin = false;

    /**
     * サーバー参加時に読み込む最大キャッシュ数
     * loadFullCacheOnJoinがtrueの場合に適用
     */
    public int maxCacheLoadOnJoin = 500;

    // ============================================================
    // llama-server自動起動設定
    // ============================================================

    /**
     * llama-serverを自動起動するかどうか
     * trueの場合、MOD起動時に自動的にllama-serverを起動します
     */
    public boolean autoStartLlamaServer = true;

    /**
     * llama-serverのポート番号
     */
    public int llamaServerPort = 8080;

    /**
     * llama-serverのホストアドレス
     */
    public String llamaServerHost = "0.0.0.0";

    /**
     * llama-serverのコンテキストサイズ
     */
    public int llamaContextSize = 4096;

    /**
     * llama-serverのGPU層数（-1 = 全レイヤー）
     */
    public int llamaGpuLayers = -1;

    /**
     * llama-serverのバッチサイズ
     */
    public int llamaBatchSize = 512;

    /**
     * llama-serverのスレッド数
     */
    public int llamaThreads = 8;

    /**
     * llama-serverの並列処理数
     */
    public int llamaParallel = 4;

    /**
     * llama-serverのメインGPU ID
     */
    public int llamaMainGpu = 0;

    /**
     * llama-server用のGPU ID（-1 = 全GPU使用、0以上 = 特定GPU）
     * Minecraft用とLLM用でGPUを分離する場合に使用
     */
    public int llamaGpuId = -1;

    /**
     * 使用するモデルファイル名
     * 例: "gemma-3-4b-q4.gguf"
     */
    public String llamaModelFile = "gemma-3-4b-q4.gguf";

    /**
     * プロンプトキャッシングを有効にするか
     */
    public boolean llamaCachePrompt = true;

    /**
     * メトリクスを有効にするか
     */
    public boolean llamaMetrics = false;

    // ============================================================
    // 翻訳メッセージの色設定
    // ============================================================

    /**
     * [翻訳]ラベルの色コード
     * Minecraftの色コード: §0-§9, §a-§f
     * 例: "§7" = グレー, "§a" = 明るい緑, "§e" = 黄色
     */
    public String translationLabelColor = "§7";

    /**
     * 翻訳テキストの色コード
     */
    public String translationTextColor = "§f";

    /**
     * 原文ラベル（"(原文: "）の色コード
     */
    public String originalLabelColor = "§8";

    /**
     * 原文テキストの色コード
     */
    public String originalTextColor = "§7";

    /**
     * [送信翻訳]ラベルの色コード
     */
    public String outgoingLabelColor = "§7";

    /**
     * 送信翻訳テキストの色コード
     */
    public String outgoingTextColor = "§f";

    /**
     * 送信原文ラベルの色コード
     */
    public String outgoingOriginalLabelColor = "§8";

    /**
     * 送信原文テキストの色コード
     */
    public String outgoingOriginalTextColor = "§7";

    // ============================================================
    // シングルトンインスタンス取得
    // ============================================================

    /**
     * 設定のシングルトンインスタンスを取得
     * @return ModConfigインスタンス
     */
    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    // ============================================================
    // 設定の保存・読み込み
    // ============================================================

    /**
     * 設定ファイルを読み込みます
     * ファイルが存在しない場合はデフォルト設定を返します
     * @return ModConfigインスタンス
     */
    public static ModConfig load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                // ファイルが空でないかチェック
                reader.mark(1);
                if (reader.read() == -1) {
                    System.err.println("[ChatLLM] Config file is empty, using defaults");
                    ModConfig defaultConfig = new ModConfig();
                    defaultConfig.save();
                    return defaultConfig;
                }
                reader.reset();

                ModConfig config = GSON.fromJson(reader, ModConfig.class);
                if (config != null) {
                    return config;
                }
            } catch (com.google.gson.JsonSyntaxException e) {
                System.err.println("[ChatLLM] Invalid JSON in config file, using defaults: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("[ChatLLM] Failed to load config: " + e.getMessage());
            }
        }

        // デフォルト設定を作成して保存
        ModConfig defaultConfig = new ModConfig();
        defaultConfig.save();
        return defaultConfig;
    }

    /**
     * 設定をファイルに保存します
     */
    public void save() {
        try {
            // 設定ディレクトリが存在しない場合は作成
            Files.createDirectories(CONFIG_PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            System.err.println("[ChatLLM] Failed to save config: " + e.getMessage());
        }
    }

    // ============================================================
    // ヘルパーメソッド
    // ============================================================

    /**
     * システムプロンプトを取得（ターゲット言語を埋め込み）
     * @return フォーマット済みシステムプロンプト
     */
    public String getFormattedSystemPrompt() {
        return String.format(systemPrompt, targetLanguage, targetLanguage);
    }

    /**
     * 設定をリロード
     */
    public static void reload() {
        INSTANCE = load();
    }
}
