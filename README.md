# Chat LLM Translation

Minecraft用のリアルタイムチャット翻訳MOD - ローカルLLMを使用して完全オフライン動作

## 特徴

- **受信メッセージの自動翻訳**（英語→日本語）
- **送信メッセージの自動翻訳**（日本語→英語）
- **ローカルLLM使用**（完全オフライン動作可能）
- **RAGベースの翻訳キャッシュ**（同じ文章は即座に翻訳）
- **サーバーごとのキャッシュ管理**（各サーバーの翻訳履歴を個別に保存）
- **全キャッシュ読み込みオプション**（サーバー参加時に過去の翻訳を全て読み込み可能）
- **llama-server自動起動**（MOD起動時に自動的にLLMサーバーを起動・終了時に停止）
- **マルチGPU対応**（Minecraft用とLLM用でGPUを分離可能）
- **自動ダウンロード機能**（初回起動時に必要なファイルを自動取得）
- **プロンプトキャッシング**（高速化）
- **会話コンテキスト対応**（過去の会話を考慮した自然な翻訳）

## 必要環境

- **Minecraft**: 1.21.x
- **Fabric Loader**: 0.16.0以降
- **Fabric API**: 0.110.0以降
- **Java**: 21以降
- **空きディスク容量**: 約3GB（モデルとLLMサーバー用）
- **GPU**: NVIDIA GPU推奨（CPUでも動作可能だが遅い）

## インストール方法

### 1. MODファイルをダウンロード

[Releases](https://github.com/Rangesa/Chat-LLM-Translation/releases)から最新版の`chat_llm_translation-x.x.x.jar`をダウンロード

### 2. MODフォルダに配置

`.minecraft/mods/`フォルダに`chat_llm_translation-x.x.x.jar`を配置

### 3. Minecraftを起動

初回起動時に自動的に以下をダウンロードします：
- LLMモデル（Gemma 3 4B Q4_K_M - 約2.5GB、5分割）
- llama-server（LLM推論エンジン - 約100MB）

ダウンロードには数分〜数十分かかります。プログレスバーで進捗を確認できます。

### 4. llama-serverの自動起動

**デフォルトでは自動起動が有効です！**

ダウンロード完了後、MODが自動的にllama-serverを起動します。手動起動は不要です。

自動起動を無効にしたい場合は、`.minecraft/config/chat_llm_translation.json`で以下を設定：
```json
{
  "autoStartLlamaServer": false
}
```

## 使い方

### 基本操作

1. **Minecraftでマルチプレイサーバーに参加**（llama-serverは自動起動されます）
2. **チャットを受信すると自動的に翻訳されます**
   - 形式: `§7[翻訳] §f翻訳文 §8(原文: 元の文章)`
3. **日本語でチャットを送信すると自動的に英語に翻訳されます**

### 設定

**GUIで設定（推奨）:**

Mod MenuからChatLLM Translationの設定を開くと、以下が設定できます：
- **GPU選択**: 使用するGPUを選択（マルチGPU環境向け）
- **モデル選択**: 使用するLLMモデルを選択
- **自動起動**: llama-serverの自動起動を有効/無効
- **サーバー起動/停止**: ボタンでllama-serverを起動・停止

**設定ファイルで設定:**

設定ファイル: `.minecraft/config/chat_llm_translation.json`

```json
{
  "llmServerUrl": "http://localhost:8080",
  "translationEnabled": true,
  "autoTranslateIncoming": true,
  "autoTranslateOutgoing": false,
  "targetLanguage": "Japanese",
  "outgoingTargetLanguage": "English",
  "outgoingTranslationTimeout": 1500,
  "loadFullCacheOnJoin": false,
  "maxCacheLoadOnJoin": 500,
  "autoStartLlamaServer": true,
  "llamaServerPort": 8080,
  "llamaContextSize": 4096,
  "llamaGpuLayers": -1,
  "llamaGpuId": -1,
  "llamaCachePrompt": true
}
```

#### 主要な設定項目

**翻訳設定:**
- `loadFullCacheOnJoin`: サーバー参加時に過去の全翻訳キャッシュを読み込むか（デフォルト: false）
  - `true`にすると、そのサーバーでの過去の翻訳履歴を全てLLMコンテキストに読み込みます
  - 注意: 大量のキャッシュがある場合、初回翻訳が重くなる可能性があります
- `maxCacheLoadOnJoin`: サーバー参加時に読み込む最大キャッシュ数（デフォルト: 500）

**llama-server自動起動設定:**
- `autoStartLlamaServer`: llama-serverを自動起動するか（デフォルト: true）
- `llamaServerPort`: llama-serverのポート番号（デフォルト: 8080）
- `llamaContextSize`: コンテキストサイズ（デフォルト: 4096）
- `llamaGpuLayers`: GPU層数、-1で全レイヤー（デフォルト: -1）
- `llamaGpuId`: 使用するGPU ID、-1で全GPU（デフォルト: -1）
  - Minecraftとは別のGPUを使う場合に設定（例: 1）
- `llamaCachePrompt`: プロンプトキャッシングを有効にするか（デフォルト: true）

**翻訳メッセージの色設定:**
- `translationLabelColor`: [翻訳]ラベルの色（デフォルト: "§7" = グレー）
- `translationTextColor`: 翻訳テキストの色（デフォルト: "§f" = 白）
- `originalLabelColor`: (原文: の色（デフォルト: "§8" = ダークグレー）
- `originalTextColor`: 原文テキストの色（デフォルト: "§7" = グレー）
- `outgoingLabelColor`: [送信翻訳]ラベルの色（デフォルト: "§7" = グレー）
- `outgoingTextColor`: 送信翻訳テキストの色（デフォルト: "§f" = 白）
- `outgoingOriginalLabelColor`: 送信原文ラベルの色（デフォルト: "§8" = ダークグレー）
- `outgoingOriginalTextColor`: 送信原文テキストの色（デフォルト: "§7" = グレー）

**Minecraftの色コード:**
- `§0` = 黒, `§1` = 濃い青, `§2` = 濃い緑, `§3` = 濃い水色
- `§4` = 濃い赤, `§5` = 濃い紫, `§6` = 金色, `§7` = グレー
- `§8` = ダークグレー, `§9` = 青, `§a` = 緑, `§b` = 水色
- `§c` = 赤, `§d` = ピンク, `§e` = 黄色, `§f` = 白

### マルチGPU設定（FPS低下対策）

**自動起動を使用する場合（推奨）:**

設定ファイル `.minecraft/config/chat_llm_translation.json` で：
```json
{
  "llamaGpuId": 1
}
```

これでMinecraftとは別のGPU（ID: 1）でllama-serverが起動します。

**手動起動を使用する場合:**

`autoStartLlamaServer: false`に設定してから、以下のスクリプトで起動：

**Windows** (`start-llama-server.bat`):
```bat
set "CUDA_VISIBLE_DEVICES=1"
cd .minecraft\chat_llm_translation\bin
llama-server.exe --model ..\models\gemma-3-4b-q4.gguf --port 8080 --n-gpu-layers -1 --cache-prompt
```

**Linux/Mac** (`start-llama-server.sh`):
```bash
export CUDA_VISIBLE_DEVICES=1
cd .minecraft/chat_llm_translation/bin
./llama-server --model ../models/gemma-3-4b-q4.gguf --port 8080 --n-gpu-layers -1 --cache-prompt
```

GPU ID:
- `0`: Minecraft用GPU
- `1`: LLM用GPU（推奨）

## 技術仕様

### アーキテクチャ

```
Minecraft MOD (Fabric)
    ↓ HTTP POST (非同期)
llama-server (localhost:8080)
    ↓
Gemma 3 4B (GGUF Q4_K_M)
    ↓
翻訳結果
```

### 主要コンポーネント

- **ChatHandler**: 翻訳処理の中核
- **LLMClient**: llama-server APIクライアント（完全非同期）
- **RAGStorage**: 翻訳結果のキャッシュ
- **ChatHistory**: 会話履歴管理
- **ServerStorageManager**: サーバーごとのキャッシュ管理
- **ModelDownloader**: 分割ファイルの自動ダウンロード＆結合
- **DownloadScreen**: ダウンロード進捗表示

### サーバーごとのキャッシュ管理

各サーバーの翻訳履歴は個別に保存されます：

```
.minecraft/
└── chat_llm_translation/
    └── servers/
        ├── singleplayer/
        │   └── rag.json
        ├── play.hypixel.net/
        │   └── rag.json
        └── mc.example.com_25565/
            └── rag.json
```

これにより、サーバーごとに異なる翻訳コンテキストを維持できます。

## ビルド方法

```bash
git clone https://github.com/Rangesa/Chat-LLM-Translation.git
cd Chat-LLM-Translation
gradlew build

# 成果物: build/libs/chat_llm_translation-1.0.0+1.21.jar
```

## トラブルシューティング

### Q: ダウンロードが失敗する

A: インターネット接続を確認。[Releases](https://github.com/Rangesa/Chat-LLM-Translation/releases)から手動ダウンロードも可能。

### Q: 翻訳されない

A:
1. llama-serverが起動しているか確認
2. `http://localhost:8080/health`で動作確認
3. 設定で`translationEnabled: true`になっているか確認

### Q: FPSが落ちる

A: マルチGPU設定を使用（上記参照）

### Q: タイムアウトエラーが頻発

A: `outgoingTranslationTimeout`を増やす（例: 2000）

## ライセンス

MIT License

## クレジット

- **LLMモデル**: Google Gemma 3 4B
- **推論エンジン**: [llama.cpp](https://github.com/ggerganov/llama.cpp)
- **Minecraft MOD Framework**: Fabric

## 貢献

Pull Request歓迎！

## サポート

[Issues](https://github.com/Rangesa/Chat-LLM-Translation/issues)で報告してください。
