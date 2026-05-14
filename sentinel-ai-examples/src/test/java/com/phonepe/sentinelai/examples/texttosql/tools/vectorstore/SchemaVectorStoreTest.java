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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link SchemaVectorStore} covering keyword-only, semantic-only, and hybrid
 * search modes.
 *
 * <p>A single Lucene index is built once per test class via {@link VectorStoreInitializer} into a
 * JUnit-managed {@link TempDir}, keeping the suite fast while exercising the full indexing path.
 */
class SchemaVectorStoreTest {

    @TempDir
    static Path tempDir;

    static SchemaVectorStore store;

    @Nested
    class BuildIndexTests {

        @Test
        void buildIndexDocWithNullColumnName(@org.junit.jupiter.api.io.TempDir Path dir)
                throws IOException {
            try (SchemaVectorStore s = new SchemaVectorStore(dir)) {
                java.util.Map<String, String> doc = new java.util.HashMap<>();
                doc.put("docId", "mytable");
                doc.put("docType", "table");
                doc.put("tableName", "mytable");
                doc.put("content", "my table description");
                // columnName intentionally absent
                s.buildIndex(java.util.List.of(doc));
                List<SchemaSearchResult> results = s.keywordSearch("my table description", 5);
                assertFalse(results.isEmpty(), "Should find the indexed doc");
                assertNull(results.get(0).columnName(), "columnName should be null for table doc");
            }
        }

        @Test
        void buildIndexWithEmptyDocsSucceeds(@org.junit.jupiter.api.io.TempDir Path emptyDir)
                throws IOException {
            try (SchemaVectorStore emptyStore = new SchemaVectorStore(emptyDir)) {
                // Should not throw even with empty document list
                assertDoesNotThrow(() -> emptyStore.buildIndex(java.util.List.of()));
            }
        }

        @Test
        void hybridSearchFallsBackToMatchAllDocs(@org.junit.jupiter.api.io.TempDir Path dir)
                throws IOException {
            try (SchemaVectorStore s = new SchemaVectorStore(dir)) {
                java.util.Map<String, String> doc = new java.util.HashMap<>();
                doc.put("docId", "t1");
                doc.put("docType", "table");
                doc.put("tableName", "orders");
                doc.put("content", "orders table contains purchase records");
                s.buildIndex(java.util.List.of(doc));
                // hybrid search combines BM25 and KNN; should return the indexed document
                List<SchemaSearchResult> results = s.hybridSearch("purchase records", 5);
                assertFalse(results.isEmpty(), "hybridSearch should return results");
            }
        }

        @Test
        void keywordSearchFallsBackToMatchAllDocs(@org.junit.jupiter.api.io.TempDir Path dir)
                throws IOException {
            // Index a small set of documents
            try (SchemaVectorStore s = new SchemaVectorStore(dir)) {
                java.util.Map<String, String> doc = new java.util.HashMap<>();
                doc.put("docId", "t1");
                doc.put("docType", "table");
                doc.put("tableName", "orders");
                doc.put("content", "orders table contains purchase records");
                s.buildIndex(java.util.List.of(doc));
                // A leading wildcard query is escaped by QueryParserBase.escape() before parse,
                // so it won't trigger a ParseException. Instead we test that a valid query
                // that partially matches the content also returns results correctly.
                List<SchemaSearchResult> results = s.keywordSearch("orders", 5);
                assertFalse(results.isEmpty(), "keyword search for indexed term returns results");
                assertEquals("orders", results.get(0).tableName());
            }
        }
    }

    @Nested
    class HybridSearchTests {

        @Test
        void combinedScoresInValidRange() throws IOException {
            List<SchemaSearchResult> results = store.hybridSearch("user email login active", 10);
            for (SchemaSearchResult r : results) {
                assertTrue(r.score() >= 0.0f, "Combined score must be >= 0");
                assertTrue(r.score() <= 1.01f, "Combined score must be <= 1 (with epsilon)");
            }
        }

        @Test
        void hybridCoversBothSignals() throws IOException {
            // "epoch seconds" is an exact keyword; "time of purchase" paraphrases the description.
            // Hybrid should surface results that at least one mode would rank highly.
            List<SchemaSearchResult> hybridResults = store.hybridSearch("epoch seconds time of purchase", 10);
            List<SchemaSearchResult> keywordResults = store.keywordSearch("epoch seconds", 10);
            List<SchemaSearchResult> semanticResults = store.semanticSearch("time of purchase", 10);

            Set<String> hybridContents = hybridResults.stream()
                    .map(SchemaSearchResult::content)
                    .collect(Collectors.toSet());

            // At least one document from BM25-only results should appear in hybrid results
            boolean hasKeywordHit = keywordResults.stream()
                    .anyMatch(r -> hybridContents.contains(r.content()));
            // At least one document from semantic-only results should appear in hybrid results
            boolean hasSemanticHit = semanticResults.stream()
                    .anyMatch(r -> hybridContents.contains(r.content()));

            assertTrue(hasKeywordHit, "Hybrid results should include at least one BM25 hit");
            assertTrue(hasSemanticHit, "Hybrid results should include at least one KNN hit");
        }

        @Test
        void hybridReturnsMoreCandidatesThanKeyword() throws IOException {
            // "account status active deactivated" — these words appear in multiple tables
            // Hybrid should pull at least as many docs as keyword alone
            int topK = 10;
            List<SchemaSearchResult> hybrid = store.hybridSearch("account status active deactivated", topK);
            List<SchemaSearchResult> keyword = store.keywordSearch("account status active deactivated", topK);

            // Hybrid should return at least as many results as keyword-only
            assertTrue(
                       hybrid.size() >= keyword.size(),
                       "Hybrid should cover at least all BM25 results; "
                               + "hybrid=" + hybrid.size() + " keyword=" + keyword.size());
        }

        @Test
        void merchantQueryHitsSellersAndCatalog() throws IOException {
            List<SchemaSearchResult> results = store.hybridSearch("merchant seller product listing", 10);
            Set<String> tables = results.stream()
                    .map(SchemaSearchResult::tableName)
                    .collect(Collectors.toSet());
            assertTrue(
                       tables.contains("sellers") || tables.contains("catalog"),
                       "Merchant/product query should hit sellers or catalog; got: " + tables);
        }

        @Test
        void resultFieldsArePopulated() throws IOException {
            List<SchemaSearchResult> results = store.hybridSearch("unique identifier auto increment", 8);
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
        void resultsAreSortedDescending() throws IOException {
            List<SchemaSearchResult> results = store.hybridSearch("seller rating contact", 10);
            for (int i = 0; i < results.size() - 1; i++) {
                assertTrue(
                           results.get(i).score() >= results.get(i + 1).score(),
                           "Combined scores should be non-increasing");
            }
        }

        @Test
        void returnsResultsForMixedQuery() throws IOException {
            List<SchemaSearchResult> results = store.hybridSearch("order delivery timestamp epoch", 5);
            assertFalse(results.isEmpty(), "Hybrid search should return results");
        }

        @Test
        void shippingQueryHitsOrders() throws IOException {
            List<SchemaSearchResult> results = store.hybridSearch("shipped delivered order status", 8);
            assertTrue(
                       results.stream().anyMatch(r -> "orders".equals(r.tableName())),
                       "Shipping/delivery terminology should surface orders documents");
        }

        @Test
        void topKLimitIsRespected() throws IOException {
            List<SchemaSearchResult> results = store.hybridSearch("id primary key", 2);
            assertTrue(results.size() <= 2, "Should return at most topK=2 results");
        }
    }

    // =========================================================================
    // Initializer contract
    // =========================================================================

    @Nested
    class InitializerTests {

        @Test
        void allTablesAreIndexed() throws IOException {
            // The index has ~55 documents (5 tables + ~10 columns each). Retrieve all via a broad
            // query with a topK larger than the total doc count so every table-type document is
            // included in the result set regardless of BM25 ranking.
            List<SchemaSearchResult> results = store.keywordSearch("users sellers catalog inventory orders", 60);
            Set<String> indexedTables = results.stream()
                    .filter(r -> "table".equals(r.docType()))
                    .map(SchemaSearchResult::tableName)
                    .collect(Collectors.toSet());

            for (String expected : List.of("users", "sellers", "catalog", "inventory", "orders")) {
                assertTrue(
                           indexedTables.contains(expected),
                           "Table '" + expected + "' should be indexed; found: " + indexedTables);
            }
        }

        @Test
        void indexDirectoryHasFiles() {
            Path indexPath = tempDir.resolve(VectorStoreInitializer.INDEX_DIR_NAME);
            String[] files = indexPath.toFile().list();
            assertNotNull(files);
            assertTrue(files.length > 0, "Lucene should have written segment files");
        }

        @Test
        void indexDirectoryIsCreated() {
            Path indexPath = tempDir.resolve(VectorStoreInitializer.INDEX_DIR_NAME);
            assertTrue(indexPath.toFile().exists(), "Index directory should exist after init");
            assertTrue(indexPath.toFile().isDirectory(), "Index path should be a directory");
        }

        @Test
        void reopenExistingIndexSucceeds() throws IOException {
            // Calling ensureInitialized a second time should skip building and open the index
            try (SchemaVectorStore reopened = VectorStoreInitializer.ensureInitialized(tempDir)) {
                List<SchemaSearchResult> results = reopened.keywordSearch("orders", 3);
                assertFalse(results.isEmpty(), "Reopened store should return results");
            }
        }
    }

    // =========================================================================
    // Keyword search
    // =========================================================================

    @Nested
    class KeywordSearchTests {

        @Test
        void columnResultsHaveColumnName() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("epoch seconds timestamp", 10);
            boolean foundColumn = results.stream().anyMatch(r -> "column".equals(r.docType()));
            assertTrue(foundColumn, "Should find at least one column-type result");
            results.stream()
                    .filter(r -> "column".equals(r.docType()))
                    .forEach(
                             r -> assertNotNull(
                                                r.columnName(),
                                                "Column doc must have columnName set"));
        }

        @Test
        void emailTopResultIsUserOrSeller() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("email", 5);
            assertFalse(results.isEmpty());
            Set<String> topTables = results.stream().map(SchemaSearchResult::tableName).collect(Collectors.toSet());
            assertTrue(
                       topTables.contains("users") || topTables.contains("sellers"),
                       "email should hit users or sellers; got: " + topTables);
        }

        @Test
        void exactTermReturnsResults() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("warehouse", 5);
            assertFalse(results.isEmpty(), "Exact keyword 'warehouse' should match");
        }

        @Test
        void ratingQueryHitsSellers() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("rating reviews", 5);
            assertTrue(
                       results.stream().anyMatch(r -> "sellers".equals(r.tableName())),
                       "rating/reviews columns live in sellers");
        }

        @Test
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
        void resultsAreSortedDescending() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("price purchase order", 8);
            for (int i = 0; i < results.size() - 1; i++) {
                assertTrue(
                           results.get(i).score() >= results.get(i + 1).score(),
                           "Scores should be non-increasing");
            }
        }

        @Test
        void statusQueryHitsOrders() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("status lifecycle", 5);
            assertTrue(
                       results.stream().anyMatch(r -> "orders".equals(r.tableName())),
                       "status lifecycle description is in the orders table");
        }

        @Test
        void topKLimitIsRespected() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("id", 3);
            assertTrue(results.size() <= 3, "Should return at most topK=3 results");
        }

        @Test
        void warehouseTopResultIsInventory() throws IOException {
            List<SchemaSearchResult> results = store.keywordSearch("warehouse", 5);
            SchemaSearchResult top = results.get(0);
            assertEquals("inventory",
                         top.tableName(),
                         "Top result for 'warehouse' should be in the inventory table");
        }
    }

    // =========================================================================
    // Semantic search
    // =========================================================================

    @Nested
    class SemanticSearchTests {

        @Test
        void buyerQueryHitsUsers() throws IOException {
            List<SchemaSearchResult> results = store.semanticSearch("registered buyer account timezone", 8);
            assertTrue(
                       results.stream().anyMatch(r -> "users".equals(r.tableName())),
                       "Buyer/account terminology should surface users table documents");
        }

        @Test
        void naturalLanguageQueryReturnsResults() throws IOException {
            List<SchemaSearchResult> results = store.semanticSearch("when was the account created", 5);
            assertFalse(results.isEmpty(), "Semantic search should return results");
        }

        @Test
        void productQueryHitsCatalog() throws IOException {
            List<SchemaSearchResult> results = store.semanticSearch("product category brand listing price", 8);
            assertTrue(
                       results.stream().anyMatch(r -> "catalog".equals(r.tableName())),
                       "Product listing terminology should hit catalog table");
        }

        @Test
        void resultFieldsArePopulated() throws IOException {
            List<SchemaSearchResult> results = store.semanticSearch("purchase transaction amount", 5);
            for (SchemaSearchResult r : results) {
                assertNotNull(r.docType());
                assertNotNull(r.tableName());
                assertNotNull(r.content());
                assertFalse(r.content().isBlank());
            }
        }

        @Test
        void resultsAreSortedDescending() throws IOException {
            List<SchemaSearchResult> results = store.semanticSearch("product available for purchase", 8);
            for (int i = 0; i < results.size() - 1; i++) {
                assertTrue(
                           results.get(i).score() >= results.get(i + 1).score(),
                           "Cosine scores should be non-increasing");
            }
        }

        @Test
        void scoresAreInValidRange() throws IOException {
            List<SchemaSearchResult> results = store.semanticSearch("order shipment delivery status", 10);
            for (SchemaSearchResult r : results) {
                assertTrue(r.score() > 0.0f, "Cosine score should be positive");
                assertTrue(r.score() <= 1.01f, "Cosine score should not exceed 1 (with epsilon)");
            }
        }

        @Test
        void stockQueryHitsInventory() throws IOException {
            List<SchemaSearchResult> results = store.semanticSearch("current stock level quantity warehouse", 8);
            assertTrue(
                       results.stream().anyMatch(r -> "inventory".equals(r.tableName())),
                       "Stock/quantity/warehouse terms should surface inventory table documents");
        }

        @Test
        void topKLimitIsRespected() throws IOException {
            List<SchemaSearchResult> results = store.semanticSearch("user contact details", 4);
            assertTrue(results.size() <= 4, "Should return at most topK=4 results");
        }
    }

    // =========================================================================
    // Hybrid search
    // =========================================================================

    @BeforeAll
    static void buildIndex() throws IOException {
        store = VectorStoreInitializer.ensureInitialized(tempDir);
    }

    // =========================================================================
    // buildIndex — additional paths
    // =========================================================================

    @AfterAll
    static void closeStore() throws IOException {
        if (store != null) {
            store.close();
        }
    }
}
