package jp.chat_llm_translation.client;

import jp.chat_llm_translation.Chat_llm_translation;
import jp.chat_llm_translation.chat.ChatHandler;
import jp.chat_llm_translation.downloader.ModelDownloader;
import jp.chat_llm_translation.llm.LlamaServerManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;

/**
 * クライアント側の初期化とイベント処理
 */
public class Chat_llm_translationClient implements ClientModInitializer {

    private long downloadFinishTime = -1;

    @Override
    public void onInitializeClient() {
        Chat_llm_translation.LOGGER.info("[ChatLLM] Initializing client-side features");

        // 初回起動時に必要なファイルをバックグラウンドでダウンロード
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            Chat_llm_translation.LOGGER.info("[ChatLLM] Checking required files in background...");
            ModelDownloader.ensureFilesExist().thenRun(() -> {
                // ダウンロード完了後、llama-serverを自動起動
                LlamaServerManager serverManager = Chat_llm_translation.getLlamaServerManager();
                if (serverManager != null) {
                    serverManager.startServer().thenAccept(success -> {
                        if (success) {
                            Chat_llm_translation.LOGGER.info("[ChatLLM] llama-server auto-start successful");
                        } else {
                            Chat_llm_translation.LOGGER.warn("[ChatLLM] llama-server auto-start failed or disabled");
                        }
                    });
                }
            });
        });

        // HUDにダウンロード進捗を描画するコールバックを登録 (インゲーム用)
        HudRenderCallback.EVENT.register(this::renderDownloadHud);
        // GUI画面でもHUDを描画するコールバックを登録
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterRender(screen).register((s, drawContext, mouseX, mouseY, tickDelta) -> {
                renderDownloadHud(drawContext, client.getRenderTickCounter());
            });
        });


        // --- 既存のイベントハンドラ ---
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleChatMessage(message, overlay));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> handleChatMessage(message, false));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            Chat_llm_translation.LOGGER.info("[ChatLLM] Joined server");
            ChatHandler chatHandler = Chat_llm_translation.getChatHandler();
            if (chatHandler != null) {
                String serverAddress = (client.getCurrentServerEntry() != null) ? client.getCurrentServerEntry().address : null;
                Chat_llm_translation.LOGGER.info("[ChatLLM] Server address: {}", serverAddress);
                chatHandler.onServerJoin(serverAddress);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            Chat_llm_translation.LOGGER.info("[ChatLLM] Disconnected from server");
            ChatHandler chatHandler = Chat_llm_translation.getChatHandler();
            if (chatHandler != null) {
                chatHandler.onServerLeave();
            }
        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            Chat_llm_translation.LOGGER.info("[ChatLLM] Saving data before client shutdown");
            ChatHandler handler = Chat_llm_translation.getChatHandler();
            if (handler != null) handler.saveRAG();

            LlamaServerManager serverManager = Chat_llm_translation.getLlamaServerManager();
            if (serverManager != null) serverManager.stopServer();
        });

        Chat_llm_translation.LOGGER.info("[ChatLLM] Client-side initialization complete");
    }

    /**
     * ダウンロード進捗をHUDに描画する
     */
    private void renderDownloadHud(DrawContext context, RenderTickCounter tickCounter) {
        ModelDownloader.DownloadProgress progress = ModelDownloader.currentProgress;
        if (progress == null) {
            return;
        }

        // 描画状態を保存
        context.getMatrices().pushMatrix();

        try {
            // ダウンロード完了/エラー後、5秒経ったらHUDを消す
            if (progress.isFinished) {
                if (downloadFinishTime == -1) {
                    downloadFinishTime = System.currentTimeMillis();
                }
                if (System.currentTimeMillis() - downloadFinishTime > 5000) {
                    ModelDownloader.currentProgress = null;
                    downloadFinishTime = -1;
                    return;
                }
            }

            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            int screenHeight = context.getScaledWindowHeight();

            // HUDの描画設定
            int padding = 5;
            int lineHeight = textRenderer.fontHeight + 2;
            int boxWidth = 180;
            int boxHeight = (lineHeight * 3) + (padding * 2); // 3行表示用に高さを調整
            int boxX = padding;
            int boxY = screenHeight - boxHeight - padding;
            int bgColor = 0x80333333; // 半透明の濃い灰色

            // 背景を描画
            context.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, bgColor);

            // テキストを描画
            Text titleText = Text.literal("§e[ChatLLM Downloader]"); // 黄色
            Text statusText = Text.literal(String.format("§f%s: %d%%", progress.item, progress.getProgressPercent())); // 白
            Text messageText = Text.literal("§7" + progress.message); // 灰色

            context.drawTextWithShadow(textRenderer, titleText, boxX + padding, boxY + padding, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, statusText, boxX + padding, boxY + padding + lineHeight, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, messageText, boxX + padding, boxY + padding + (lineHeight * 2), 0xFFFFFF);

            // プログレスバー
            int barY = boxY + boxHeight - padding - 3;
            int barWidth = boxWidth - (padding * 2);
            int barColor = progress.item.equals("Error") ? 0xFFFF5555 : 0xFF55FF55; // エラー時は赤、通常は緑

            context.fill(boxX + padding, barY, boxX + padding + barWidth, barY + 2, 0x80333333); // 背景
            context.fill(boxX + padding, barY, boxX + padding + (int)(barWidth * progress.progress), barY + 2, barColor); // 進捗

        } finally {
            // 描画状態を復元
            context.getMatrices().popMatrix();
        }
    }

    private void handleChatMessage(net.minecraft.text.Text message, boolean overlay) {
        if (overlay) return;

        ChatHandler handler = Chat_llm_translation.getChatHandler();
        if (handler == null) return;

        String messageText = message.getString();
        if (messageText.contains("[翻訳]") || messageText.contains("[送信翻訳]")) return;
        if (messageText.contains("[System]") || messageText.contains("[Server]") || messageText.contains("[ChatLLM]")) return;

        String playerName = "Unknown";
        String chatMessage = messageText;

        if (messageText.startsWith("<") && messageText.contains(">")) {
            int closeBracketIndex = messageText.indexOf(">");
            if (closeBracketIndex != -1 && closeBracketIndex < messageText.length() - 1) {
                playerName = messageText.substring(1, closeBracketIndex).trim();
                chatMessage = messageText.substring(closeBracketIndex + 1).trim();
            }
        } else if (messageText.contains(":")) {
            int colonIndex = messageText.indexOf(":");
            if (colonIndex != -1 && colonIndex < messageText.length() - 1) {
                playerName = messageText.substring(0, colonIndex).trim();
                chatMessage = messageText.substring(colonIndex + 1).trim();
            }
        }

        if (chatMessage.isEmpty()) return;

        String finalPlayerName = playerName;
        String finalChatMessage = chatMessage;
        handler.handleIncomingMessage(finalPlayerName, finalChatMessage)
                .thenAccept(translated -> {
                    MinecraftClient.getInstance().execute(() -> {
                        handler.displayTranslation(finalChatMessage, translated);
                    });
                })
                .exceptionally(ex -> {
                    Chat_llm_translation.LOGGER.error("[ChatLLM] Failed to translate incoming message: {}", finalChatMessage, ex);
                    return null;
                });
    }
}
