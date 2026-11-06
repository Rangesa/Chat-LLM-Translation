package jp.chat_llm_translation.rag;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import jp.chat_llm_translation.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval-Augmented Generation) ストレージ
 * チャット翻訳のコンテキストとして使用する知識ベースを管理します
 */
public class RAGStorage {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .create();
    private static final Path DEFAULT_STORAGE_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("chat_llm_translation_rag.json");

    private final Path storagePath;

    /**
     * RAGエントリ
     */
    public static class RAGEntry {
        public String originalText;
        public String translatedText;
        public String context; // 追加のコンテキスト情報
        public Instant timestamp;
        public int useCount; // 使用回数（人気度）

        public RAGEntry(String originalText, String translatedText, String context) {
            this.originalText = originalText;
            this.translatedText = translatedText;
            this.context = context;
            this.timestamp = Instant.now();
            this.useCount = 0;
        }

        /**
         * エントリのスコアを計算（類似度と人気度の組み合わせ）
         *
         * @param query 検索クエリ
         * @return スコア（高いほど関連性が高い）
         */
        public double calculateScore(String query) {
            double similarityScore = calculateSimilarity(query, originalText);
            double popularityScore = Math.log(useCount + 1) / 10.0; // 人気度ボーナス
            return similarityScore + popularityScore;
        }

        /**
         * 簡易的な類似度計算（共通単語数ベース）
         */
        private double calculateSimilarity(String query, String text) {
            Set<String> queryWords = tokenize(query.toLowerCase());
            Set<String> textWords = tokenize(text.toLowerCase());

            if (queryWords.isEmpty() || textWords.isEmpty()) {
                return 0.0;
            }

            // 共通単語数を計算
            Set<String> intersection = new HashSet<>(queryWords);
            intersection.retainAll(textWords);

            // Jaccard係数を計算
            Set<String> union = new HashSet<>(queryWords);
            union.addAll(textWords);

            return (double) intersection.size() / union.size();
        }

        /**
         * テキストをトークンに分割
         */
        private Set<String> tokenize(String text) {
            return Arrays.stream(text.split("\\s+"))
                    .filter(word -> word.length() > 1) // 1文字の単語は除外
                    .collect(Collectors.toSet());
        }
    }

    private final Map<String, RAGEntry> storage;
    private final ModConfig config;

    /**
     * コンストラクタ（デフォルトパス使用）
     */
    public RAGStorage() {
        this(DEFAULT_STORAGE_PATH);
    }

    /**
     * コンストラクタ（カスタムパス指定）
     *
     * @param storagePath ストレージファイルのパス
     */
    public RAGStorage(Path storagePath) {
        this.storagePath = storagePath;
        this.config = ModConfig.getInstance();
        this.storage = new HashMap<>();
        load();
    }

    /**
     * エントリを追加または更新
     *
     * @param originalText 元のテキスト
     * @param translatedText 翻訳されたテキスト
     * @param context コンテキスト情報
     */
    public synchronized void addOrUpdate(String originalText, String translatedText, String context) {
        if (!config.ragEnabled) {
            return;
        }

        // キーは元のテキストの正規化版
        String key = normalizeKey(originalText);

        // 既存エントリがあれば更新、なければ新規追加
        RAGEntry entry = storage.getOrDefault(key, new RAGEntry(originalText, translatedText, context));
        entry.translatedText = translatedText; // 翻訳を更新
        entry.timestamp = Instant.now(); // タイムスタンプを更新
        entry.useCount++; // 使用回数をインクリメント

        storage.put(key, entry);

        // ストレージサイズの制限
        if (storage.size() > config.ragMaxEntries) {
            pruneOldEntries();
        }

        // 定期的に保存（100エントリごと）
        if (storage.size() % 100 == 0) {
            save();
        }
    }

    /**
     * クエリに基づいて関連するエントリを検索
     *
     * @param query 検索クエリ
     * @param topK 返す最大エントリ数
     * @return 関連性の高い順にソートされたRAGEntryのリスト
     */
    public synchronized List<RAGEntry> search(String query, int topK) {
        if (!config.ragEnabled || storage.isEmpty()) {
            return Collections.emptyList();
        }

        return storage.values().stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(entry, entry.calculateScore(query)))
                .filter(pair -> pair.getValue() > 0.1) // 低スコアのエントリは除外
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue())) // スコアの降順
                .limit(topK)
                .map(AbstractMap.SimpleEntry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 完全一致検索
     *
     * @param originalText 元のテキスト
     * @return マッチするRAGEntry、存在しない場合はnull
     */
    public synchronized RAGEntry getExactMatch(String originalText) {
        String key = normalizeKey(originalText);
        RAGEntry entry = storage.get(key);
        if (entry != null) {
            entry.useCount++; // 使用回数をインクリメント
        }
        return entry;
    }

    /**
     * 古いエントリを削除（LRU方式）
     */
    private void pruneOldEntries() {
        int targetSize = (int) (config.ragMaxEntries * 0.8); // 80%まで削減

        List<Map.Entry<String, RAGEntry>> entries = new ArrayList<>(storage.entrySet());

        // タイムスタンプと使用回数を考慮してソート
        entries.sort((a, b) -> {
            double scoreA = a.getValue().useCount + a.getValue().timestamp.getEpochSecond() / 1000000.0;
            double scoreB = b.getValue().useCount + b.getValue().timestamp.getEpochSecond() / 1000000.0;
            return Double.compare(scoreA, scoreB);
        });

        // 古いエントリを削除
        int toRemove = storage.size() - targetSize;
        for (int i = 0; i < toRemove && i < entries.size(); i++) {
            storage.remove(entries.get(i).getKey());
        }
    }

    /**
     * キーの正規化（小文字化、空白の正規化）
     */
    private String normalizeKey(String text) {
        return text.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    /**
     * ストレージをファイルに保存
     */
    public synchronized void save() {
        try {
            Files.createDirectories(storagePath.getParent());

            try (Writer writer = Files.newBufferedWriter(storagePath)) {
                GSON.toJson(storage, writer);
            }

            if (config.debugMode) {
                System.out.println("[ChatLLM] RAG storage saved: " + storage.size() + " entries to " + storagePath);
            }
        } catch (IOException e) {
            System.err.println("[ChatLLM] Failed to save RAG storage: " + e.getMessage());
        }
    }

    /**
     * ストレージをファイルから読み込み
     */
    private void load() {
        if (!Files.exists(storagePath)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(storagePath)) {
            // ファイルが空でないかチェック
            reader.mark(1);
            if (reader.read() == -1) {
                if (config.debugMode) {
                    System.out.println("[ChatLLM] RAG storage file is empty, starting fresh");
                }
                return;
            }
            reader.reset();

            Type type = new TypeToken<Map<String, RAGEntry>>(){}.getType();
            Map<String, RAGEntry> loaded = GSON.fromJson(reader, type);

            if (loaded != null) {
                storage.clear();
                storage.putAll(loaded);

                if (config.debugMode) {
                    System.out.println("[ChatLLM] RAG storage loaded: " + storage.size() + " entries");
                }
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            System.err.println("[ChatLLM] Invalid JSON in RAG storage, starting fresh: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[ChatLLM] Failed to load RAG storage: " + e.getMessage());
        }
    }

    /**
     * ストレージをクリア
     */
    public synchronized void clear() {
        storage.clear();
        save();
    }

    /**
     * ストレージのサイズを取得
     */
    public synchronized int size() {
        return storage.size();
    }

    /**
     * Instant用のカスタムTypeAdapter
     * Java 21でGsonがInstantをシリアライズできない問題を回避
     */
    private static class InstantTypeAdapter extends TypeAdapter<Instant> {
        @Override
        public void write(JsonWriter out, Instant value) throws IOException {
            if (value == null) {
                out.nullValue();
            } else {
                out.value(value.toString());
            }
        }

        @Override
        public Instant read(JsonReader in) throws IOException {
            if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
                in.nextNull();
                return null;
            }
            String timestamp = in.nextString();
            return Instant.parse(timestamp);
        }
    }
}
