package jp.chat_llm_translation.config;

import jp.chat_llm_translation.Chat_llm_translation;
import jp.chat_llm_translation.llm.LlamaServerManager;
import jp.chat_llm_translation.util.SystemUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

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
    private TextFieldWidget serverUrlField;
    private TextFieldWidget targetLanguageField;
    private TextFieldWidget onlineApiUrlField;
    private TextFieldWidget onlineApiKeyField;
    private CyclingButtonWidget<Boolean> translationToggleButton;
    private CyclingButtonWidget<Boolean> autoTranslateIncomingButton;
    private CyclingButtonWidget<Boolean> autoTranslateOutgoingButton;
    private CyclingButtonWidget<Boolean> ragToggleButton;
    private CyclingButtonWidget<Boolean> debugToggleButton;
    private CyclingButtonWidget<Boolean> useOnlineApiButton;

    // --- サーバー管理ウィジェット & 状態 ---
    private int selectedGpuId;
    private String selectedModel;
    private boolean serverRunning;
    private ButtonWidget serverButton;
    private int tickCounter = 0;
    private boolean serverHealthy = false;
    private int healthCheckCounter = 0;

    public ConfigScreen(Screen parent) {
        super(Text.literal("Chat LLM Translation - 統合設定"));
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
        this.serverUrlField = new TextFieldWidget(this.textRenderer, centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Text.literal("LLM Server URL"));
        this.serverUrlField.setText(config.llmServerUrl);
        this.addDrawableChild(this.serverUrlField);
        currentY += spacing;

        // 翻訳先言語
        this.targetLanguageField = new TextFieldWidget(this.textRenderer, centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Text.literal("Target Language"));
        this.targetLanguageField.setText(config.targetLanguage);
        this.addDrawableChild(this.targetLanguageField);
        currentY += spacing;

        // 各種トグルボタン
        translationToggleButton = addDrawableChild(CyclingButtonWidget.onOffBuilder(config.translationEnabled)
                .build(centerX - fullWidth / 2, currentY, halfWidth, buttonHeight, Text.literal("翻訳機能"), (btn, val) -> config.translationEnabled = val));
        autoTranslateIncomingButton = addDrawableChild(CyclingButtonWidget.onOffBuilder(config.autoTranslateIncoming)
                .build(centerX + fullWidth / 2 - halfWidth, currentY, halfWidth, buttonHeight, Text.literal("受信を自動翻訳"), (btn, val) -> config.autoTranslateIncoming = val));
        currentY += spacing;

        autoTranslateOutgoingButton = addDrawableChild(CyclingButtonWidget.onOffBuilder(config.autoTranslateOutgoing)
                .build(centerX - fullWidth / 2, currentY, halfWidth, buttonHeight, Text.literal("送信を自動翻訳"), (btn, val) -> config.autoTranslateOutgoing = val));
        ragToggleButton = addDrawableChild(CyclingButtonWidget.onOffBuilder(config.ragEnabled)
                .build(centerX + fullWidth / 2 - halfWidth, currentY, halfWidth, buttonHeight, Text.literal("RAG機能"), (btn, val) -> config.ragEnabled = val));
        currentY += spacing;

        debugToggleButton = addDrawableChild(CyclingButtonWidget.onOffBuilder(config.debugMode)
                .build(centerX - fullWidth / 2, currentY, halfWidth, buttonHeight, Text.literal("デバッグモード"), (btn, val) -> config.debugMode = val));
        currentY += spacing;

        // --- オンラインAPI設定セクション ---
        useOnlineApiButton = addDrawableChild(CyclingButtonWidget.onOffBuilder(config.useOnlineApi)
                .build(centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Text.literal("オンラインAPIを使用"), (btn, val) -> {
                    config.useOnlineApi = val;
                    updateOnlineApiFieldsVisibility();
                }));
        currentY += spacing;

        // オンラインAPI URL
        this.onlineApiUrlField = new TextFieldWidget(this.textRenderer, centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Text.literal("Online API URL"));
        this.onlineApiUrlField.setText(config.onlineApiUrl);
        this.onlineApiUrlField.setPlaceholder(Text.literal("https://api.openai.com/v1/chat/completions"));
        this.addDrawableChild(this.onlineApiUrlField);
        currentY += spacing;

        // オンラインAPI Key
        this.onlineApiKeyField = new TextFieldWidget(this.textRenderer, centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Text.literal("API Key"));
        this.onlineApiKeyField.setText(config.onlineApiKey);
        this.onlineApiKeyField.setPlaceholder(Text.literal("sk-..."));
        this.addDrawableChild(this.onlineApiKeyField);
        currentY += spacing + 10;

        updateOnlineApiFieldsVisibility(); // 初期状態を設定

        // --- サーバー管理セクション ---
        currentY += 15; // セクションタイトル用のスペース
        // GPU選択
        List<String> gpus = SystemUtils.getAvailableGPUs();
        int currentGpuIndex = findGpuIndex(gpus, selectedGpuId);
        this.addDrawableChild(CyclingButtonWidget.builder((String value) -> Text.literal(value)).values(gpus).initially(gpus.get(currentGpuIndex))
                .build(centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Text.literal("GPU選択"), (btn, val) -> selectedGpuId = SystemUtils.extractGpuId(val)));
        currentY += spacing;

        // モデル選択
        List<String> models = SystemUtils.getAvailableModels();
        int currentModelIndex = models.contains(selectedModel) ? models.indexOf(selectedModel) : 0;
        this.addDrawableChild(CyclingButtonWidget.builder((String value) -> Text.literal(value)).values(models).initially(models.get(currentModelIndex))
                .build(centerX - fullWidth / 2, currentY, fullWidth, buttonHeight, Text.literal("モデル選択"), (btn, val) -> selectedModel = val));
        currentY += spacing;

        // 自動起動トグル
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.autoStartLlamaServer)
                .build(centerX - fullWidth / 2, currentY, halfWidth, buttonHeight, Text.literal("サーバー自動起動"), (btn, val) -> config.autoStartLlamaServer = val));

        // サーバー起動/停止ボタン
        serverButton = ButtonWidget.builder(Text.literal(serverRunning ? "サーバー停止" : "サーバー起動"), this::handleServerButton)
                .dimensions(centerX + fullWidth / 2 - halfWidth, currentY, halfWidth, buttonHeight).build();
        this.addDrawableChild(serverButton);
        currentY += spacing + 10;

        // --- 下部ボタン ---
        // 保存して閉じる
        this.addDrawableChild(ButtonWidget.builder(Text.literal("保存して閉じる"), button -> {
                    this.saveConfig();
                    this.close();
                })
                .dimensions(centerX - 155, this.height - 28, 100, buttonHeight).build());

        // キャンセル
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.close())
                .dimensions(centerX - 50, this.height - 28, 100, buttonHeight).build());

        // リセット
        this.addDrawableChild(ButtonWidget.builder(Text.literal("リセット"), button -> {
                    // TODO: Implement reset logic
                })
                .dimensions(centerX + 55, this.height - 28, 100, buttonHeight).build());
    }

    private void handleServerButton(ButtonWidget button) {
        if (serverManager == null) return;
        if (serverRunning) {
            serverManager.stopServer();
            serverRunning = false;
            button.setMessage(Text.literal("サーバー起動"));
        } else {
            saveConfig(); // 起動前に現在の設定を保存
            serverManager.startServer().thenAccept(success -> {
                if (success) {
                    serverRunning = true;
                    if (client != null) client.execute(() -> button.setMessage(Text.literal("サーバー停止")));
                }
            });
        }
    }

    private void saveConfig() {
        config.llmServerUrl = this.serverUrlField.getText();
        config.targetLanguage = this.targetLanguageField.getText();
        config.onlineApiUrl = this.onlineApiUrlField.getText();
        config.onlineApiKey = this.onlineApiKeyField.getText();
        config.llamaGpuId = selectedGpuId;
        config.llamaModelFile = selectedModel;
        config.save();

        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.literal("§a設定を保存しました"), false);
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

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
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, centerX, 15, 0xFFFFFF);

        // セクションタイトル
        context.drawCenteredTextWithShadow(this.textRenderer, "--- 翻訳設定 ---", centerX, 25, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, "--- サーバー管理 ---", centerX, 195, 0xAAAAAA);


        // ステータス表示
        int statusY = this.height - 55;
        String statusIcon = serverRunning ? (serverHealthy ? "§a●" : "§e●") : "§c●";
        String statusLabel = serverRunning ? (serverHealthy ? "Running" : "Starting...") : "Stopped";
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Status: " + statusIcon + " §f" + statusLabel), centerX, statusY, 0xFFFFFF);

        if (serverRunning) {
            statusY += 12;
            String details = String.format("§7Port: §f%d §8| §7GPU: §f%s §8| §7Model: §f%s",
                config.llamaServerPort,
                config.llamaGpuId == -1 ? "All" : String.valueOf(config.llamaGpuId),
                config.llamaModelFile.length() > 20 ? config.llamaModelFile.substring(0, 17) + "..." : config.llamaModelFile
            );
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(details), centerX, statusY, 0xFFFFFF);
        }
    }

    private void updateServerStatus() {
        if (serverManager != null) {
            boolean newStatus = serverManager.isRunning();
            if (newStatus != serverRunning) {
                serverRunning = newStatus;
                if (serverButton != null) {
                    serverButton.setMessage(Text.literal(serverRunning ? "サーバー停止" : "サーバー起動"));
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
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}