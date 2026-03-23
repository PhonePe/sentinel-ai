/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.examples.texttosql.tools.vectorstore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;

/**
 * Lucene-based vector store for schema documents (tables and columns).
 *
 * <p>Each indexed document stores both a {@code TextField} for BM25 keyword search and a {@link
 * KnnFloatVectorField} for approximate nearest-neighbour (ANN) semantic search. {@link
 * #hybridSearch} combines both signals by L2-normalising their scores and blending them with
 * configurable weights.
 *
 * <p>The index is written once by {@link VectorStoreInitializer} and opened read-only on
 * subsequent runs. All search operations open a fresh {@link DirectoryReader} so they always see
 * the latest committed state.
 */
@Slf4j
public class SchemaVectorStore implements AutoCloseable {

    // ── Lucene field names ───────────────────────────────────────────────────
    static final String FIELD_DOC_ID = "doc_id";
    static final String FIELD_DOC_TYPE = "doc_type";
    static final String FIELD_TABLE_NAME = "table_name";
    static final String FIELD_COLUMN_NAME = "column_name";
    static final String FIELD_CONTENT = "content";
    static final String FIELD_VECTOR = "vector";

    /** Weight applied to the normalised BM25 score (0–1). KNN weight = 1 − BM25_WEIGHT. */
    private static final float BM25_WEIGHT = 0.5f;

    private final FSDirectory directory;
    private final HashTextEmbedder embedder;

    /**
     * Opens (or creates) the Lucene {@link FSDirectory} at {@code indexPath}.
     *
     * @param indexPath filesystem path where the index files are stored
     */
    public SchemaVectorStore(Path indexPath) throws IOException {
        this.directory = FSDirectory.open(indexPath);
        this.embedder = new HashTextEmbedder();
    }

    // ── Index writing ────────────────────────────────────────────────────────

    /**
     * Builds a new index from the provided documents, replacing any existing index at the same
     * path.
     *
     * <p>Called once by {@link VectorStoreInitializer}; not intended for incremental updates.
     *
     * @param documents list of entries to index; each entry must have {@code docId}, {@code
     *     docType}, {@code tableName}, and {@code content}; {@code columnName} may be {@code null}
     */
    public void buildIndex(List<Map<String, String>> documents) throws IOException {
        try (StandardAnalyzer analyzer = new StandardAnalyzer()) {
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            // CREATE erases any existing index so re-initialisation is idempotent
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

            try (IndexWriter writer = new IndexWriter(directory, config)) {
                for (Map<String, String> entry : documents) {
                    Document doc = new Document();

                    String content = entry.get("content");
                    float[] vector = embedder.embed(content);

                    doc.add(new StringField(FIELD_DOC_ID, entry.get("docId"), Field.Store.YES));
                    doc.add(new StringField(FIELD_DOC_TYPE, entry.get("docType"), Field.Store.YES));
                    doc.add(
                            new StringField(
                                    FIELD_TABLE_NAME, entry.get("tableName"), Field.Store.YES));

                    String columnName = entry.get("columnName");
                    if (columnName != null && !columnName.isBlank()) {
                        doc.add(
                                new StringField(
                                        FIELD_COLUMN_NAME, columnName, Field.Store.YES));
                    }

                    doc.add(new TextField(FIELD_CONTENT, content, Field.Store.YES));
                    doc.add(
                            new KnnFloatVectorField(
                                    FIELD_VECTOR, vector, VectorSimilarityFunction.COSINE));

                    writer.addDocument(doc);
                }
                writer.commit();
                log.info("Indexed {} schema documents into vector store", documents.size());
            }
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────

    /**
     * Runs a hybrid search combining BM25 keyword matching and KNN cosine-similarity vector
     * search.
     *
     * <p>Algorithm:
     *
     * <ol>
     *   <li>Embed {@code query} into a float vector using {@link HashTextEmbedder}.
     *   <li>Execute a BM25 full-text query against the {@code content} field.
     *   <li>Execute a KNN query against the {@code vector} field.
     *   <li>Collect all candidate documents from both result sets.
     *   <li>Normalise each score set to [0, 1] by dividing by its maximum.
     *   <li>Blend: {@code combinedScore = BM25_WEIGHT × normBM25 + (1 − BM25_WEIGHT) × normKNN}.
     *   <li>Sort by combined score (descending) and return the top {@code topK} results.
     * </ol>
     *
     * @param query natural-language or keyword query string
     * @param topK maximum number of results to return
     * @return ranked list of matching schema documents
     */
    public List<SchemaSearchResult> hybridSearch(String query, int topK) throws IOException {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            searcher.setSimilarity(new BM25Similarity());

            float[] queryVector = embedder.embed(query);
            int candidateCount = Math.max(topK * 3, 20);

            // ── BM25 search ──────────────────────────────────────────────────
            Map<Integer, Float> bm25Scores = new HashMap<>();
            try (StandardAnalyzer analyzer = new StandardAnalyzer()) {
                QueryParser parser = new QueryParser(FIELD_CONTENT, analyzer);
                org.apache.lucene.search.Query bm25Query;
                try {
                    bm25Query = parser.parse(QueryParser.escape(query));
                } catch (ParseException e) {
                    log.debug("BM25 query parse failed for '{}', using match-all: {}", query, e.getMessage());
                    bm25Query = new org.apache.lucene.search.MatchAllDocsQuery();
                }
                TopDocs bm25Docs = searcher.search(bm25Query, candidateCount);
                for (ScoreDoc sd : bm25Docs.scoreDocs) {
                    bm25Scores.put(sd.doc, sd.score);
                }
            }

            // ── KNN vector search ────────────────────────────────────────────
            Map<Integer, Float> knnScores = new HashMap<>();
            KnnFloatVectorQuery knnQuery =
                    new KnnFloatVectorQuery(FIELD_VECTOR, queryVector, candidateCount);
            TopDocs knnDocs = searcher.search(knnQuery, candidateCount);
            for (ScoreDoc sd : knnDocs.scoreDocs) {
                // Cosine similarity is already in [0,1] for normalised vectors
                knnScores.put(sd.doc, sd.score);
            }

            // ── Combine and rank ─────────────────────────────────────────────
            float maxBm25 = bm25Scores.values().stream().max(Float::compareTo).orElse(1.0f);
            float maxKnn = knnScores.values().stream().max(Float::compareTo).orElse(1.0f);

            Map<Integer, Float> combinedScores = new HashMap<>();
            for (int docId : bm25Scores.keySet()) {
                float normBm25 = bm25Scores.get(docId) / maxBm25;
                float normKnn = knnScores.getOrDefault(docId, 0.0f) / maxKnn;
                combinedScores.put(docId, BM25_WEIGHT * normBm25 + (1 - BM25_WEIGHT) * normKnn);
            }
            for (int docId : knnScores.keySet()) {
                if (!combinedScores.containsKey(docId)) {
                    float normKnn = knnScores.get(docId) / maxKnn;
                    combinedScores.put(docId, (1 - BM25_WEIGHT) * normKnn);
                }
            }

            List<Map.Entry<Integer, Float>> ranked =
                    new ArrayList<>(combinedScores.entrySet());
            ranked.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

            List<SchemaSearchResult> results = new ArrayList<>();
            for (int i = 0; i < Math.min(topK, ranked.size()); i++) {
                Map.Entry<Integer, Float> entry = ranked.get(i);
                Document doc = searcher.storedFields().document(entry.getKey());

                String columnName = doc.get(FIELD_COLUMN_NAME);
                results.add(
                        new SchemaSearchResult(
                                doc.get(FIELD_DOC_TYPE),
                                doc.get(FIELD_TABLE_NAME),
                                columnName,
                                doc.get(FIELD_CONTENT),
                                entry.getValue()));
            }
            return results;
        }
    }

    @Override
    public void close() throws IOException {
        directory.close();
    }
}
