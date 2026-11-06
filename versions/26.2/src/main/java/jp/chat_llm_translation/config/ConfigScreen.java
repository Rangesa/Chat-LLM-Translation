package jp.chat_llm_translation.config;

import jp.chat_llm_translation.Chat_llm_translation;
import jp.chat_llm_translation.llm.LlamaServerManager;
import jp.chat_llm_translation.util.SystemUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

/**
 * MODの統合設定画面
 * 翻訳設定とサーバー管理機能をすべて含みます
 */
public class ConfigScreen extends Screen {
    private final Screen parent;
    private final ModConfig config;
    private final LlamaServerManager serverManager;

    // --- 翻訳設定ウィジェット ---
    private EditBox serverUrlField;
    private EditBox targetLanguageField;
    private EditBox onlineApiUrlField;
    private EditBox onlineApiKeyField;
    private CycleButton<Boolean> translationToggleButton;
    private CycleButton<Boolean> autoTranslateIncomingButton;
    private CycleButton<Boolean> autoTranslateOutgoingButton;
    private CycleButton<Boolean> ragToggleButton;
    private CycleButton<Boolean> debugToggleButton;
    private CycleButton<Boolean> useOnlineApiButton;

    // --- サーバー管理ウィジェット & 状態 ---
    private int selectedGpuId;
    private String selectedModel;
    private boolean serverRunning;
    private Button serverButton;
    private int tickCounter = 0;
    private boolean serverHealthy = false;
    private int healthCheckCounter = 0;

    public ConfigScreen(Screen parent) {
        super(Component.literal("Chat LLM Translation - 統合設定"));
        this.parent = parent;
        this.config = ModConfig.getInstance();
        this.serverManager = Chat_llm_translation.getLlamaServerManager();

        // サーバー管理用の状態を初期化
        if (this.serverManager != null) {
            this.selectedGpuId = config.llamaGpuId;
            this.selectedModel = config.llamaModelFile;
            this.serverRunning = serverManager.isRunning();
        }
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int fullWidth = 310;
        int halfWidth = 150;
        int buttonHeight = 20;
        int spacing = 24;
        int currentY = 35;

        // --- 翻訳設定セクション ---
        // LLMサーバーURL
        this.serverUrlField = new EditBox(this.font, centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Component.literal("LLM Server URL"));
        this.serverUrlField.setValue(config.llmServerUrl);
        this.addRenderableWidget(this.serverUrlField);
        currentY += spacing;

        // 翻訳先言語
        this.targetLanguageField = new EditBox(this.font, centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Component.literal("Target Language"));
        this.targetLanguageField.setValue(config.targetLanguage);
        this.addRenderableWidget(this.targetLanguageField);
        currentY += spacing;

        // 各種トグルボタン
        translationToggleButton = addRenderableWidget(CycleButton.onOffBuilder(config.translationEnabled)
                .create(centerX - fullWidth / 2, currentY, halfWidth, buttonHeight, Component.literal("翻訳機能"), (btn, val) -> config.translationEnabled = val));
        autoTranslateIncomingButton = addRenderableWidget(CycleButton.onOffBuilder(config.autoTranslateIncoming)
                .create(centerX + fullWidth / 2 - halfWidth, currentY, halfWidth, buttonHeight, Component.literal("受信を自動翻訳"), (btn, val) -> config.autoTranslateIncoming = val));
        currentY += spacing;

        autoTranslateOutgoingButton = addRenderableWidget(CycleButton.onOffBuilder(config.autoTranslateOutgoing)
                .create(centerX - fullWidth / 2, currentY, halfWidth, buttonHeight, Component.literal("送信を自動翻訳"), (btn, val) -> config.autoTranslateOutgoing = val));
        ragToggleButton = addRenderableWidget(CycleButton.onOffBuilder(config.ragEnabled)
                .create(centerX + fullWidth / 2 - halfWidth, currentY, halfWidth, buttonHeight, Component.literal("RAG機能"), (btn, val) -> config.ragEnabled = val));
        currentY += spacing;

        debugToggleButton = addRenderableWidget(CycleButton.onOffBuilder(config.debugMode)
                .create(centerX - fullWidth / 2, currentY, halfWidth, buttonHeight, Component.literal("デバッグモード"), (btn, val) -> config.debugMode = val));
        currentY += spacing;

        // --- オンラインAPI設定セクション ---
        useOnlineApiButton = addRenderableWidget(CycleButton.onOffBuilder(config.useOnlineApi)
                .create(centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Component.literal("オンラインAPIを使用"), (btn, val) -> {
                    config.useOnlineApi = val;
                    updateOnlineApiFieldsVisibility();
                }));
        currentY += spacing;

        // オンラインAPI URL
        this.onlineApiUrlField = new EditBox(this.font, centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Component.literal("Online API URL"));
        this.onlineApiUrlField.setValue(config.onlineApiUrl);
        this.onlineApiUrlField.setHint(Component.literal("https://api.openai.com/v1/chat/completions"));
        this.addRenderableWidget(this.onlineApiUrlField);
        currentY += spacing;

        // オンラインAPI Key
        this.onlineApiKeyField = new EditBox(this.font, centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Component.literal("API Key"));
        this.onlineApiKeyField.setValue(config.onlineApiKey);
        this.onlineApiKeyField.setHint(Component.literal("sk-..."));
        this.addRenderableWidget(this.onlineApiKeyField);
        currentY += spacing + 10;

        updateOnlineApiFieldsVisibility(); // 初期状態を設定

        // --- サーバー管理セクション ---
        currentY += 15; // セクションタイトル用のスペース
        // GPU選択
        List<String> gpus = SystemUtils.getAvailableGPUs();
        int currentGpuIndex = findGpuIndex(gpus, selectedGpuId);
        this.addRenderableWidget(CycleButton.builder((String value) -> Component.literal(value), gpus.get(currentGpuIndex)).withValues(gpus)
                .create(centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Component.literal("GPU選択"), (btn, val) -> selectedGpuId = SystemUtils.extractGpuId(val)));
        currentY += spacing;

        // モデル選択
        List<String> models = SystemUtils.getAvailableModels();
        int currentModelIndex = models.contains(selectedModel) ? models.indexOf(selectedModel) : 0;
        this.addRenderableWidget(CycleButton.builder((String value) -> Component.literal(value), models.get(currentModelIndex)).withValues(models)
                .create(centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Component.literal("モデル選択"), (btn, val) -> selectedModel = val));
        currentY += spacing;

        // 自動起動トグル
        this.addRenderableWidget(CycleButton.onOffBuilder(config.autoStartLlamaServer)
                .create(centerX - fullWidth / 2, currentY, halfWidth, buttonHeight, Component.literal("サーバー自動起動"), (btn, val) -> config.autoStartLlamaServer = val));

        // サーバー起動/停止ボタン
        serverButton = Button.builder(Component.literal(serverRunning ? "サーバー停止" : "サーバー起動"), this::handleServerButton)
                .bounds(centerX + fullWidth / 2 - halfWidth, currentY, halfWidth, buttonHeight).build();
        this.addRenderableWidget(serverButton);
        currentY += spacing + 10;

        // --- 下部ボタン ---
        // 保存して閉じる
        this.addRenderableWidget(Button.builder(Component.literal("保存して閉じる"), button -> {
                    this.saveConfig();
                    this.onClose();
                })
                .bounds(centerX - 155, this.height - 28, 100, buttonHeight).build());

        // キャンセル
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(centerX - 50, this.height - 28, 100, buttonHeight).build());

        // リセット
        this.addRenderableWidget(Button.builder(Component.literal("リセット"), button -> {
                    // TODO: Implement reset logic
                })
                .bounds(centerX + 55, this.height - 28, 100, buttonHeight).build());
    }

    private void handleServerButton(Button button) {
        if (serverManager == null) return;
        if (serverRunning) {
            serverManager.stopServer();
            serverRunning = false;
            button.setMessage(Component.literal("サーバー起動"));
        } else {
            saveConfig(); // 起動前に現在の設定を保存
            serverManager.startServer().thenAccept(success -> {
                if (success) {
                    serverRunning = true;
                    if (minecraft != null) minecraft.execute(() -> button.setMessage(Component.literal("サーバー停止")));
                }
            });
        }
    }

    private void saveConfig() {
        config.llmServerUrl = this.serverUrlField.getValue();
        config.targetLanguage = this.targetLanguageField.getValue();
        config.onlineApiUrl = this.onlineApiUrlField.getValue();
        config.onlineApiKey = this.onlineApiKeyField.getValue();
        config.llamaGpuId = selectedGpuId;
        config.llamaModelFile = selectedModel;
        config.save();

        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.sendSystemMessage(Component.literal("§a設定を保存しました"));
        }
    }

    /**
     * オンラインAPI入力フィールドの表示/非表示を切り替え
     */
    private void updateOnlineApiFieldsVisibility() {
        boolean visible = config.useOnlineApi;
        if (this.onlineApiUrlField != null) {
            this.onlineApiUrlField.setVisible(visible);
        }
        if (this.onlineApiKeyField != null) {
            this.onlineApiKeyField.setVisible(visible);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.extractBackground(context, mouseX, mouseY, delta);
        super.extractRenderState(context, mouseX, mouseY, delta);

        // リアルタイム更新処理
        tickCounter++;
        if (tickCounter >= 20) {
            tickCounter = 0;
            updateServerStatus();
        }
        if (serverRunning) {
            healthCheckCounter++;
            if (healthCheckCounter >= 60) {
                healthCheckCounter = 0;
                checkServerHealth();
            }
        }

        int centerX = this.width / 2;

        // タイトル
        context.centeredText(this.font, this.title, centerX, 15, 0xFFFFFFFF);

        // セクションタイトル
        context.centeredText(this.font, "--- 翻訳設定 ---", centerX, 25, 0xFFAAAAAA);
        context.centeredText(this.font, "--- サーバー管理 ---", centerX, 195, 0xFFAAAAAA);


        // ステータス表示
        int statusY = this.height - 55;
        String statusIcon = serverRunning ? (serverHealthy ? "§a●" : "§e●") : "§c●";
        String statusLabel = serverRunning ? (serverHealthy ? "Running" : "Starting...") : "Stopped";
        context.centeredText(this.font, Component.literal("Status: " + statusIcon + " §f" + statusLabel), centerX, statusY, 0xFFFFFFFF);

        if (serverRunning) {
            statusY += 12;
            String details = String.format("§7Port: §f%d §8| §7GPU: §f%s §8| §7Model: §f%s",
                config.llamaServerPort,
                config.llamaGpuId == -1 ? "All" : String.valueOf(config.llamaGpuId),
                config.llamaModelFile.length() > 20 ? config.llamaModelFile.substring(0, 17) + "..." : config.llamaModelFile
            );
            context.centeredText(this.font, Component.literal(details), centerX, statusY, 0xFFFFFFFF);
        }
    }

    private void updateServerStatus() {
        if (serverManager != null) {
            boolean newStatus = serverManager.isRunning();
            if (newStatus != serverRunning) {
                serverRunning = newStatus;
                if (serverButton != null) {
                    serverButton.setMessage(Component.literal(serverRunning ? "サーバー停止" : "サーバー起動"));
                }
                if (!newStatus) serverHealthy = false;
            }
        }
    }

    private void checkServerHealth() {
        new Thread(() -> {
            try {
                URL url = new URL(config.llmServerUrl + "/health");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(1000);
                connection.setReadTimeout(1000);
                serverHealthy = (connection.getResponseCode() == 200);
                connection.disconnect();
            } catch (IOException e) {
                serverHealthy = false;
            }
        }, "llama-server-health-check").start();
    }

    private int findGpuIndex(List<String> gpus, int gpuId) {
        for (int i = 0; i < gpus.size(); i++) {
            if (SystemUtils.extractGpuId(gpus.get(i)) == gpuId) {
                return i;
            }
        }
        return 0; // Default to "All GPUs"
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
