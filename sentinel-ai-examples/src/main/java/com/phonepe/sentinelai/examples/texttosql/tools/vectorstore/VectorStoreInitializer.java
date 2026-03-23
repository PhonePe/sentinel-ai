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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Bootstraps the Lucene schema vector store from {@code schema_descriptions.json}.
 *
 * <p>On first run the index does not exist, so {@link #ensureInitialized} reads {@code
 * /db/schema_descriptions.json} from the classpath, converts every table and column description
 * into a {@link SchemaVectorStore} document, and writes a new index under {@code
 * {dataDir}/lucene-schema-index/}.
 *
 * <p>On subsequent runs the index directory already exists, so initialization is skipped and the
 * store is opened directly — making startup fast without re-indexing.
 */
@Slf4j
public class VectorStoreInitializer {

    private static final String SCHEMA_JSON_RESOURCE = "/db/schema_descriptions.json";
    static final String INDEX_DIR_NAME = "lucene-schema-index";

    private VectorStoreInitializer() {}

    public static void main(String[] args) throws Exception {
        final Path dataDir = Path.of("sentinel-ai-examples/.data");
        try (SchemaVectorStore vectorStore = VectorStoreInitializer.ensureInitialized(dataDir)) {
            // ignored
        }
    }

    /**
     * Returns a ready-to-use {@link SchemaVectorStore}, building the index if it does not already
     * exist.
     *
     * @param dataDir directory where the index will be (or already is) stored; typically the
     *     {@code .data/} directory alongside the SQLite database
     * @return an open {@link SchemaVectorStore} backed by the Lucene index
     * @throws IOException if the index cannot be read or written
     */
    public static SchemaVectorStore ensureInitialized(Path dataDir) throws IOException {
        Path indexPath = dataDir.resolve(INDEX_DIR_NAME);

        if (isIndexPresent(indexPath)) {
            log.info("Schema vector store index found at {}, skipping initialisation", indexPath);
        } else {
            log.info("Schema vector store index not found at {}, building index", indexPath);
            Files.createDirectories(indexPath);
            buildIndex(indexPath);
            log.info("Schema vector store index built successfully at {}", indexPath);
        }

        return new SchemaVectorStore(indexPath);
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /**
     * An index is considered present when the directory exists and contains at least one file
     * (Lucene writes several segment files on first commit).
     */
    private static boolean isIndexPresent(Path indexPath) {
        if (!Files.exists(indexPath)) {
            return false;
        }
        try (var files = Files.list(indexPath)) {
            return files.findAny().isPresent();
        } catch (IOException e) {
            log.warn("Failed to list index directory {}: {}", indexPath, e.getMessage());
            return false;
        }
    }

    /**
     * Loads {@code schema_descriptions.json} from the classpath and writes a fresh Lucene index.
     */
    private static void buildIndex(Path indexPath) throws IOException {
        List<Map<String, String>> documents = loadDocuments();
        SchemaVectorStore store = new SchemaVectorStore(indexPath);
        store.buildIndex(documents);
        // store.close() is intentionally not called here — the caller receives ownership
        // via ensureInitialized, so the store stays open after writing.
    }

    /**
     * Reads {@code schema_descriptions.json} from the classpath and converts each table and column
     * description into an indexable document map.
     *
     * <p>Each table yields one document with {@code docType = "table"}.
     * Each column within a table yields one document with {@code docType = "column"}, where the
     * content is "{tableName} {columnName}: {columnDescription}".
     */
    private static List<Map<String, String>> loadDocuments() throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        InputStream resource =
                VectorStoreInitializer.class.getResourceAsStream(SCHEMA_JSON_RESOURCE);
        if (resource == null) {
            throw new IOException(
                    "Could not find " + SCHEMA_JSON_RESOURCE + " on the classpath");
        }

        JsonNode root = mapper.readTree(resource);
        JsonNode tables = root.get("tables");
        if (tables == null || !tables.isArray()) {
            throw new IOException("schema_descriptions.json must contain a top-level 'tables' array");
        }

        List<Map<String, String>> documents = new ArrayList<>();

        for (JsonNode tableNode : tables) {
            String tableName = tableNode.get("name").asText();
            String tableDescription = tableNode.get("description").asText();

            // Table-level document
            documents.add(
                    buildDocEntry(
                            tableName,
                            "table",
                            tableName,
                            null,
                            tableName + ": " + tableDescription));

            // Column-level documents
            JsonNode columns = tableNode.get("columns");
            if (columns != null && columns.isArray()) {
                for (JsonNode colNode : columns) {
                    String columnName = colNode.get("name").asText();
                    String columnDescription = colNode.get("description").asText();
                    //String dataType = colNode.get("dataType").asText();

                    String content =
                            tableName
                                    + " "
                                    + columnName
                                    + " "
                                    + columnDescription;

                    documents.add(
                            buildDocEntry(
                                    tableName + "." + columnName,
                                    "column",
                                    tableName,
                                    columnName,
                                    content));
                }
            }
        }

        log.info(
                "Loaded {} schema documents from {} for indexing",
                documents.size(),
                SCHEMA_JSON_RESOURCE);
        return documents;
    }

    private static Map<String, String> buildDocEntry(
            String docId,
            String docType,
            String tableName,
            String columnName,
            String content) {
        Map<String, String> entry = new HashMap<>();
        entry.put("docId", docId);
        entry.put("docType", docType);
        entry.put("tableName", tableName);
        if (columnName != null) {
            entry.put("columnName", columnName);
        }
        entry.put("content", content);
        return entry;
    }
}
