# Chat LLM Translation

Minecraft用のリアルタイムチャット翻訳MOD - ローカルLLMを使用して完全オフライン動作

## 特徴

- ✅ **受信メッセージの自動翻訳**（英語→日本語）
- ✅ **送信メッセージの自動翻訳**（日本語→英語）
- ✅ **ローカルLLM使用**（完全オフライン動作可能）
- ✅ **RAGベースの翻訳キャッシュ**（同じ文章は即座に翻訳）
- ✅ **マルチGPU対応**（Minecraft用とLLM用でGPUを分離可能）
- ✅ **自動ダウンロード機能**（初回起動時に必要なファイルを自動取得）
- ✅ **プロンプトキャッシング**（高速化）
- ✅ **会話コンテキスト対応**（過去の会話を考慮した自然な翻訳）

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

### 4. llama-serverを起動

`.minecraft/chat_llm_translation/bin/`フォルダに移動し、`llama-server.exe`（Windows）または`llama-server`（Linux/Mac）を起動してください。

## 使い方

### 基本操作

1. **llama-serverを起動**
2. **Minecraftでマルチプレイサーバーに参加**
3. **チャットを受信すると自動的に翻訳されます**
   - 形式: `§7[翻訳] §f翻訳文 §8(原文: 元の文章)`
4. **日本語でチャットを送信すると自動的に英語に翻訳されます**

### 設定

設定ファイル: `.minecraft/config/chat_llm_translation.json`

```json
{
  "llmServerUrl": "http://localhost:8080",
  "translationEnabled": true,
  "autoTranslateIncoming": true,
  "autoTranslateOutgoing": false,
  "targetLanguage": "Japanese",
  "outgoingTargetLanguage": "English",
  "outgoingTranslationTimeout": 1500
}
```

### マルチGPU設定（FPS低下対策）

llama-server起動スクリプトを作成：

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
- **ModelDownloader**: 分割ファイルの自動ダウンロード＆結合
- **DownloadScreen**: ダウンロード進捗表示

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
