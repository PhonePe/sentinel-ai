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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Integration tests for {@link SchemaVectorStore} covering keyword-only, semantic-only, and hybrid
 * search modes.
 *
 * <p>A single Lucene index is built once per test class via {@link VectorStoreInitializer} into a
 * JUnit-managed {@link TempDir}, keeping the suite fast while exercising the full indexing path.
 */
@DisplayName("SchemaVectorStore")
class SchemaVectorStoreTest {

    @TempDir
    static Path tempDir;

    static SchemaVectorStore store;

    @BeforeAll
    static void buildIndex() throws IOException {
        store = VectorStoreInitializer.ensureInitialized(tempDir);
    }

    @AfterAll
    static void closeStore() throws IOException {
        if (store != null) {
            store.close();
        }
    }

    // =========================================================================
    // Initializer contract
    // =========================================================================

    @Nested
    @DisplayName("VectorStoreInitializer")
    class InitializerTests {

        @Test
        @DisplayName("creates index directory on first run")
        void indexDirectoryIsCreated() {
            Path indexPath = tempDir.resolve(VectorStoreInitializer.INDEX_DIR_NAME);
            assertTrue(indexPath.toFile().exists(), "Index directory should exist after init");
            assertTrue(indexPath.toFile().isDirectory(), "Index path should be a directory");
        }

        @Test
        @DisplayName("index directory is non-empty after first run")
        void indexDirectoryHasFiles() {
            Path indexPath = tempDir.resolve(VectorStoreInitializer.INDEX_DIR_NAME);
            String[] files = indexPath.toFile().list();
            assertNotNull(files);
            assertTrue(files.length > 0, "Lucene should have written segment files");
        }

        @Test
        @DisplayName("re-opening existing index returns a usable store")
        void reopenExistingIndexSucceeds() throws IOException {
            // Calling ensureInitialized a second time should skip building and open the index
            try (SchemaVectorStore reopened = VectorStoreInitializer.ensureInitialized(tempDir)) {
                List<SchemaSearchResult> results = reopened.keywordSearch("orders", 3);
                assertFalse(results.isEmpty(), "Reopened store should return results");
            }
        }

        @Test
        @DisplayName("all five e-commerce tables are indexed")
        void allTablesAreIndexed() throws IOException {
            // The index has ~55 documents (5 tables + ~10 columns each). Retrieve all via a broad
            // query with a topK larger than the total doc count so every table-type document is
            // included in the result set regardless of BM25 ranking.
            List<SchemaSearchResult> results = store.keywordSearch("users sellers catalog inventory orders", 60);
            Set<String> indexedTables =
                    results.stream()
                            .filter(r -> "table".equals(r.docType()))
                            .map(SchemaSearchResult::tableName)
                            .collect(Collectors.toSet());

            for (String expected : List.of("users", "sellers", "catalog", "inventory", "orders")) {
                assertTrue(
                        indexedTables.contains(expected),
                        "Table '" + expected + "' should be indexed; found: " + indexedTables);
            }
        }
    }

    // =========================================================================
    // Keyword search
    // =========================================================================

    @Nested
    @DisplayName("keywordSearch")
    class KeywordSearchTests {

        @Test
        @DisplayName("returns results for an exact term present in content")
        void exactTermReturnsResults() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("warehouse", 5);
            assertFalse(results.isEmpty(), "Exact keyword 'warehouse' should match");
        }

        @Test
        @DisplayName("top result for 'warehouse' belongs to inventory table")
        void warehouseTopResultIsInventory() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("warehouse", 5);
            SchemaSearchResult top = results.get(0);
            assertEquals("inventory", top.tableName(),
                    "Top result for 'warehouse' should be in the inventory table");
        }

        @Test
        @DisplayName("top result for 'email' is in users or sellers table")
        void emailTopResultIsUserOrSeller() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("email", 5);
            assertFalse(results.isEmpty());
            Set<String> topTables =
                    results.stream().map(SchemaSearchResult::tableName).collect(Collectors.toSet());
            assertTrue(
                    topTables.contains("users") || topTables.contains("sellers"),
                    "email should hit users or sellers; got: " + topTables);
        }

        @Test
        @DisplayName("results are ordered by descending score")
        void resultsAreSortedDescending() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("price purchase order", 8);
            for (int i = 0; i < results.size() - 1; i++) {
                assertTrue(
                        results.get(i).score() >= results.get(i + 1).score(),
                        "Scores should be non-increasing");
            }
        }

        @Test
        @DisplayName("topK limit is respected")
        void topKLimitIsRespected() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("id", 3);
            assertTrue(results.size() <= 3, "Should return at most topK=3 results");
        }

        @Test
        @DisplayName("each result has docType, tableName, and content populated")
        void resultFieldsArePopulated() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("buyer seller", 5);
            for (SchemaSearchResult r : results) {
                assertNotNull(r.docType(), "docType must not be null");
                assertNotNull(r.tableName(), "tableName must not be null");
                assertNotNull(r.content(), "content must not be null");
                assertFalse(r.content().isBlank(), "content must not be blank");
            }
        }

        @Test
        @DisplayName("column documents have columnName populated")
        void columnResultsHaveColumnName() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("epoch seconds timestamp", 10);
            boolean foundColumn =
                    results.stream().anyMatch(r -> "column".equals(r.docType()));
            assertTrue(foundColumn, "Should find at least one column-type result");
            results.stream()
                    .filter(r -> "column".equals(r.docType()))
                    .forEach(
                            r ->
                                    assertNotNull(
                                            r.columnName(),
                                            "Column doc must have columnName set"));
        }

        @Test
        @DisplayName("query for 'rating reviews' hits sellers table")
        void ratingQueryHitsSellers() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("rating reviews", 5);
            assertTrue(
                    results.stream().anyMatch(r -> "sellers".equals(r.tableName())),
                    "rating/reviews columns live in sellers");
        }

        @Test
        @DisplayName("query for 'status lifecycle' hits orders table")
        void statusQueryHitsOrders() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("status lifecycle", 5);
            assertTrue(
                    results.stream().anyMatch(r -> "orders".equals(r.tableName())),
                    "status lifecycle description is in the orders table");
        }
    }

    // =========================================================================
    // Semantic search
    // =========================================================================

    @Nested
    @DisplayName("semanticSearch")
    class SemanticSearchTests {

        @Test
        @DisplayName("returns results for a natural-language phrase")
        void naturalLanguageQueryReturnsResults() throws IOException {
            List<SchemaSearchResult> results =
                    store.semanticSearch("when was the account created", 5);
            assertFalse(results.isEmpty(), "Semantic search should return results");
        }

        @Test
        @DisplayName("results are ordered by descending cosine score")
        void resultsAreSortedDescending() throws IOException {
            List<SchemaSearchResult> results =
                    store.semanticSearch("product available for purchase", 8);
            for (int i = 0; i < results.size() - 1; i++) {
                assertTrue(
                        results.get(i).score() >= results.get(i + 1).score(),
                        "Cosine scores should be non-increasing");
            }
        }

        @Test
        @DisplayName("topK limit is respected")
        void topKLimitIsRespected() throws IOException {
            List<SchemaSearchResult> results = store.semanticSearch("user contact details", 4);
            assertTrue(results.size() <= 4, "Should return at most topK=4 results");
        }

        @Test
        @DisplayName("scores are in (0, 1] range for cosine similarity")
        void scoresAreInValidRange() throws IOException {
            List<SchemaSearchResult> results =
                    store.semanticSearch("order shipment delivery status", 10);
            for (SchemaSearchResult r : results) {
                assertTrue(r.score() > 0.0f, "Cosine score should be positive");
                assertTrue(r.score() <= 1.01f, "Cosine score should not exceed 1 (with epsilon)");
            }
        }

        @Test
        @DisplayName("query about stock levels hits inventory table")
        void stockQueryHitsInventory() throws IOException {
            List<SchemaSearchResult> results =
                    store.semanticSearch("current stock level quantity warehouse", 8);
            assertTrue(
                    results.stream().anyMatch(r -> "inventory".equals(r.tableName())),
                    "Stock/quantity/warehouse terms should surface inventory table documents");
        }

        @Test
        @DisplayName("query about buyers hits users table")
        void buyerQueryHitsUsers() throws IOException {
            List<SchemaSearchResult> results =
                    store.semanticSearch("registered buyer account timezone", 8);
            assertTrue(
                    results.stream().anyMatch(r -> "users".equals(r.tableName())),
                    "Buyer/account terminology should surface users table documents");
        }

        @Test
        @DisplayName("query about product listings hits catalog table")
        void productQueryHitsCatalog() throws IOException {
            List<SchemaSearchResult> results =
                    store.semanticSearch("product category brand listing price", 8);
            assertTrue(
                    results.stream().anyMatch(r -> "catalog".equals(r.tableName())),
                    "Product listing terminology should hit catalog table");
        }

        @Test
        @DisplayName("result fields are fully populated")
        void resultFieldsArePopulated() throws IOException {
            List<SchemaSearchResult> results =
                    store.semanticSearch("purchase transaction amount", 5);
            for (SchemaSearchResult r : results) {
                assertNotNull(r.docType());
                assertNotNull(r.tableName());
                assertNotNull(r.content());
                assertFalse(r.content().isBlank());
            }
        }
    }

    // =========================================================================
    // Hybrid search
    // =========================================================================

    @Nested
    @DisplayName("hybridSearch")
    class HybridSearchTests {

        @Test
        @DisplayName("returns results for a mixed keyword+semantic query")
        void returnsResultsForMixedQuery() throws IOException {
            List<SchemaSearchResult> results =
                    store.hybridSearch("order delivery timestamp epoch", 5);
            assertFalse(results.isEmpty(), "Hybrid search should return results");
        }

        @Test
        @DisplayName("results are ordered by descending combined score")
        void resultsAreSortedDescending() throws IOException {
            List<SchemaSearchResult> results =
                    store.hybridSearch("seller rating contact", 10);
            for (int i = 0; i < results.size() - 1; i++) {
                assertTrue(
                        results.get(i).score() >= results.get(i + 1).score(),
                        "Combined scores should be non-increasing");
            }
        }

        @Test
        @DisplayName("topK limit is respected")
        void topKLimitIsRespected() throws IOException {
            List<SchemaSearchResult> results = store.hybridSearch("id primary key", 2);
            assertTrue(results.size() <= 2, "Should return at most topK=2 results");
        }

        @Test
        @DisplayName("combined scores are in [0, 1] range")
        void combinedScoresInValidRange() throws IOException {
            List<SchemaSearchResult> results =
                    store.hybridSearch("user email login active", 10);
            for (SchemaSearchResult r : results) {
                assertTrue(r.score() >= 0.0f, "Combined score must be >= 0");
                assertTrue(r.score() <= 1.01f, "Combined score must be <= 1 (with epsilon)");
            }
        }

        @Test
        @DisplayName("hybrid results cover both BM25-strong and vector-strong documents")
        void hybridCoversBothSignals() throws IOException {
            // "epoch seconds" is an exact keyword; "time of purchase" paraphrases the description.
            // Hybrid should surface results that at least one mode would rank highly.
            List<SchemaSearchResult> hybridResults =
                    store.hybridSearch("epoch seconds time of purchase", 10);
            List<SchemaSearchResult> keywordResults =
                    store.keywordSearch("epoch seconds", 10);
            List<SchemaSearchResult> semanticResults =
                    store.semanticSearch("time of purchase", 10);

            Set<String> hybridContents =
                    hybridResults.stream()
                            .map(SchemaSearchResult::content)
                            .collect(Collectors.toSet());

            // At least one document from BM25-only results should appear in hybrid results
            boolean hasKeywordHit =
                    keywordResults.stream()
                            .anyMatch(r -> hybridContents.contains(r.content()));
            // At least one document from semantic-only results should appear in hybrid results
            boolean hasSemanticHit =
                    semanticResults.stream()
                            .anyMatch(r -> hybridContents.contains(r.content()));

            assertTrue(hasKeywordHit, "Hybrid results should include at least one BM25 hit");
            assertTrue(hasSemanticHit, "Hybrid results should include at least one KNN hit");
        }

        @Test
        @DisplayName("query for shipped delivered hits orders table")
        void shippingQueryHitsOrders() throws IOException {
            List<SchemaSearchResult> results =
                    store.hybridSearch("shipped delivered order status", 8);
            assertTrue(
                    results.stream().anyMatch(r -> "orders".equals(r.tableName())),
                    "Shipping/delivery terminology should surface orders documents");
        }

        @Test
        @DisplayName("query for merchant products hits sellers and catalog")
        void merchantQueryHitsSellersAndCatalog() throws IOException {
            List<SchemaSearchResult> results =
                    store.hybridSearch("merchant seller product listing", 10);
            Set<String> tables =
                    results.stream()
                            .map(SchemaSearchResult::tableName)
                            .collect(Collectors.toSet());
            assertTrue(
                    tables.contains("sellers") || tables.contains("catalog"),
                    "Merchant/product query should hit sellers or catalog; got: " + tables);
        }

        @Test
        @DisplayName("result fields are fully populated for every hit")
        void resultFieldsArePopulated() throws IOException {
            List<SchemaSearchResult> results =
                    store.hybridSearch("unique identifier auto increment", 8);
            for (SchemaSearchResult r : results) {
                assertNotNull(r.docType(), "docType must not be null");
                assertNotNull(r.tableName(), "tableName must not be null");
                assertNotNull(r.content(), "content must not be null");
                assertFalse(r.content().isBlank(), "content must not be blank");
                assertTrue(
                        "table".equals(r.docType()) || "column".equals(r.docType()),
                        "docType must be 'table' or 'column'");
            }
        }

        @Test
        @DisplayName("hybrid search returns more candidates than keyword alone for vague query")
        void hybridReturnsMoreCandidatesThanKeyword() throws IOException {
            // "account status active deactivated" — these words appear in multiple tables
            // Hybrid should pull at least as many docs as keyword alone
            int topK = 10;
            List<SchemaSearchResult> hybrid =
                    store.hybridSearch("account status active deactivated", topK);
            List<SchemaSearchResult> keyword =
                    store.keywordSearch("account status active deactivated", topK);

            // Hybrid should return at least as many results as keyword-only
            assertTrue(
                    hybrid.size() >= keyword.size(),
                    "Hybrid should cover at least all BM25 results; "
                            + "hybrid=" + hybrid.size() + " keyword=" + keyword.size());
        }
    }
}
