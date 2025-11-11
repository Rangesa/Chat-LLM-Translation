package jp.chat_llm_translation.mixin;

import jp.chat_llm_translation.Chat_llm_translation;
import jp.chat_llm_translation.chat.ChatHandler;
import jp.chat_llm_translation.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * チャット送信をインターセプトして翻訳するMixin
 */
@Mixin(ClientPlayNetworkHandler.class)
public class ChatMixin {

    /**
     * チャットメッセージ送信時に翻訳を実行（タイムアウト付き）
     * メインスレッドのブロックを最小限に抑えるため、タイムアウト時は元のメッセージを送信
     */
    @ModifyVariable(
            method = "sendChatMessage",
            at = @At("HEAD"),
            argsOnly = true
    )
    private String translateOutgoingMessage(String message) {
        ModConfig config = ModConfig.getInstance();
        ChatHandler handler = Chat_llm_translation.getChatHandler();

        // 翻訳が無効、または送信翻訳が無効の場合はそのまま返す
        if (handler == null || !config.translationEnabled || !config.autoTranslateOutgoing) {
            return message;
        }

        // 空メッセージやコマンドはスキップ
        if (message == null || message.isBlank() || message.startsWith("/")) {
            return message;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        String playerName = client.player != null ? client.player.getName().getString() : "You";

        try {
            // タイムアウト付きで翻訳を実行
            // キャッシュヒット時は即座に返る
            // LLM翻訳が必要な場合は最大outgoingTranslationTimeoutまで待機
            String translated = handler.handleOutgoingMessage(playerName, message)
                    .get(config.outgoingTranslationTimeout, TimeUnit.MILLISECONDS);

            if (config.debugMode) {
                Chat_llm_translation.LOGGER.info("[ChatLLM] Translated outgoing: {} -> {}", message, translated);
            }

            // 翻訳が成功し、元のメッセージと異なる場合は原文表示
            if (!message.equals(translated)) {
                // 翻訳結果を自分に表示（原文付き）
                if (client.player != null) {
                    String formattedMessage = config.outgoingLabelColor + "[送信翻訳] " +
                                            config.outgoingTextColor + translated +
                                            " " + config.outgoingOriginalLabelColor + "(原文: " +
                                            config.outgoingOriginalTextColor + message +
                                            config.outgoingOriginalLabelColor + ")";

                    client.player.sendMessage(
                            Text.literal(formattedMessage),
                            false
                    );
                }
            }

            return translated;
        } catch (TimeoutException e) {
            // タイムアウト時は元のメッセージを送信（ゲームのブロックを防ぐ）
            if (config.debugMode) {
                Chat_llm_translation.LOGGER.warn("[ChatLLM] Translation timeout, sending original message: {}", message);
            }
            // タイムアウト通知を表示
            if (client.player != null) {
                client.player.sendMessage(
                        Text.literal("§c[ChatLLM] 翻訳タイムアウト - 原文を送信しました"),
                        false
                );
            }
            return message;
        } catch (Exception e) {
            // その他のエラー時も元のメッセージを返す
            if (config.debugMode) {
                Chat_llm_translation.LOGGER.error("[ChatLLM] Failed to translate outgoing message", e);
            }
            return message;
        }
    }
}
