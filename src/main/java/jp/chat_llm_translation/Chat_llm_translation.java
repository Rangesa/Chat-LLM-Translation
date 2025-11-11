package jp.chat_llm_translation;

import jp.chat_llm_translation.chat.ChatHandler;
import jp.chat_llm_translation.config.ModConfig;
import jp.chat_llm_translation.llm.LlamaServerManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chat LLM Translation MOD
 * llama.cppとGemma 3 4Bを使用したリアルタイムチャット翻訳MOD
 */
public class Chat_llm_translation implements ModInitializer {
    public static final String MOD_ID = "chat_llm_translation";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ChatHandler chatHandler;
    private static LlamaServerManager llamaServerManager;

    @Override
    public void onInitialize() {
        LOGGER.info("[ChatLLM] Initializing Chat LLM Translation MOD");

        // 設定を読み込み
        ModConfig config = ModConfig.getInstance();
        LOGGER.info("[ChatLLM] Config loaded: Translation enabled = " + config.translationEnabled);

        // ChatHandlerを初期化
        chatHandler = new ChatHandler();

        // LlamaServerManagerを初期化
        llamaServerManager = new LlamaServerManager();

        // サーバー停止時にRAGストレージを保存
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[ChatLLM] Saving RAG storage...");
            if (chatHandler != null) {
                chatHandler.saveRAG();
            }
        });

        LOGGER.info("[ChatLLM] Chat LLM Translation MOD initialized successfully");
    }

    /**
     * ChatHandlerのインスタンスを取得
     *
     * @return ChatHandlerインスタンス
     */
    public static ChatHandler getChatHandler() {
        return chatHandler;
    }

    /**
     * LlamaServerManagerのインスタンスを取得
     *
     * @return LlamaServerManagerインスタンス
     */
    public static LlamaServerManager getLlamaServerManager() {
        return llamaServerManager;
    }
}
