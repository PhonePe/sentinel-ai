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

package com.phonepe.sentinelai.examples.texttosql.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;
import com.phonepe.sentinelai.examples.texttosql.tools.model.TableDescRequest;
import com.phonepe.sentinelai.examples.texttosql.tools.vectorstore.SchemaSearchResult;
import com.phonepe.sentinelai.examples.texttosql.tools.vectorstore.SchemaVectorStore;
import com.phonepe.sentinelai.examples.texttosql.tools.vectorstore.VectorStoreInitializer;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Local tools available to the {@link
 * com.phonepe.sentinelai.examples.texttosql.agent.TextToSqlAgent}.
 *
 * <p>These tools run in-process and complement the MCP SQLite tools and the remote-HTTP toolbox by
 * providing utilities that are better implemented in Java (e.g. timezone-aware timestamp
 * conversion) or that need direct JDBC access (e.g. full schema inspection with DDL comments).
 *
 * <p>Each public method annotated with {@link Tool} is registered automatically when this class is
 * passed to {@code Agent.registerTools()}.
 */
@Slf4j
public class LocalSqlTools implements ToolBox {
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String dbPath;
    private final SchemaVectorStore vectorStore;
    private final Map<String, JsonNode> schemaDescriptions;

    /**
     * Constructs LocalSqlTools and initialises the Lucene schema vector store.
     *
     * <p>The vector store index is written to {@code {dataDir}/lucene-schema-index/} the first
     * time; on subsequent runs it is opened directly from disk without re-indexing.
     *
     * @param dbPath absolute path to the SQLite database file
     * @param dataDir directory used for persisting vector store data (typically the {@code .data/}
     *     directory that also contains the SQLite file)
     */
    @SneakyThrows
    public LocalSqlTools(String dbPath, Path dataDir) {
        this.dbPath = dbPath;
        this.vectorStore = VectorStoreInitializer.ensureInitialized(dataDir);
        this.schemaDescriptions = loadSchemaDescriptions();
    }

    private static Map<String, JsonNode> loadSchemaDescriptions() {
        try (InputStream is =
                LocalSqlTools.class.getResourceAsStream("/db/schema_descriptions.json")) {
            if (is == null) {
                log.warn("schema_descriptions.json not found on classpath");
                return Map.of();
            }
            JsonNode root = MAPPER.readTree(is);
            JsonNode tables = root.get("tables");
            if (tables == null || !tables.isArray()) {
                return Map.of();
            }
            Map<String, JsonNode> result = new LinkedHashMap<>();
            for (JsonNode tableNode : tables) {
                result.put(tableNode.get("name").asText(), tableNode);
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to load schema descriptions: {}", e.getMessage());
            return Map.of();
        }
    }

    @Override
    public String name() {
        return "local_sql_tools";
    }

    // -------------------------------------------------------------------------
    // Timestamp utilities
    // -------------------------------------------------------------------------

    /**
     * Converts a Unix epoch timestamp (seconds since 1970-01-01T00:00:00Z) to a human-readable
     * date-time string in the specified IANA timezone.
     *
     * <p>All {@code *_at} columns in this database store epoch seconds. Always call this tool for
     * timestamp columns before presenting results to the user.
     *
     * @param epochSeconds Unix epoch time in seconds (e.g. 1704067200)
     * @param timezone IANA timezone ID (e.g. "Asia/Kolkata", "America/New_York", "UTC")
     * @return formatted date-time string in {@code yyyy/MM/dd HH:mm:ss} format
     */
    @Tool(
            name = "convert_epoch_to_local_dt",
            value =
                    "Convert a Unix epoch timestamp (seconds) to a formatted date-time string in the given IANA timezone. "
                            + "Use this for any column ending in '_at' (e.g. ordered_at, created_at, delivered_at). "
                            + "Returns the date-time as yyyy/MM/dd HH:mm:ss.")
    public String convertEpochToLocalDateTime(long epochSeconds, String timezone) {
        try {
            final ZoneId zoneId = ZoneId.of(timezone);
            final ZonedDateTime zdt = Instant.ofEpochSecond(epochSeconds).atZone(zoneId);
            return zdt.format(DISPLAY_FORMAT);
        } catch (Exception e) {
            log.warn(
                    "Failed to convert epoch {} with timezone {}: {}",
                    epochSeconds,
                    timezone,
                    e.getMessage());
            return "Invalid epoch or timezone: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Schema inspection
    // -------------------------------------------------------------------------

    /**
     * Returns the current date and time in the specified IANA timezone. Use this when the user
     * refers to relative dates like "today", "this week", or "last month" so you can compute the
     * correct epoch range for WHERE clauses.
     *
     * @param timezone IANA timezone ID (e.g. "Asia/Kolkata")
     * @return current date-time as {@code yyyy/MM/dd HH:mm:ss} and current epoch seconds
     */
    @Tool(
            name = "get_current_dt",
            value =
                    "Get the current date and time in the specified IANA timezone. "
                            + "Use this to resolve relative date references like 'today', 'this week', or 'last month' "
                            + "into concrete epoch-second ranges for SQL WHERE clauses.")
    public String getCurrentDateTime(String timezone) {
        try {
            final ZoneId zoneId = ZoneId.of(timezone);
            final ZonedDateTime now = ZonedDateTime.now(zoneId);
            return String.format(
                    "Current time in %s: %s (epoch seconds: %d)",
                    timezone, now.format(DISPLAY_FORMAT), now.toEpochSecond());
        } catch (Exception e) {
            return "Invalid timezone: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Vector store search
    // -------------------------------------------------------------------------

    /**
     * Searches the schema vector store using hybrid (keyword + semantic) search to find the most
     * relevant tables and columns for a given natural-language question.
     *
     * <p>Use this to quickly discover which tables and columns are relevant before writing a SQL
     * query. The search combines BM25 keyword matching with cosine-similarity vector search over
     * table and column descriptions indexed from {@code schema_descriptions.json}.
     *
     * @param query natural-language description of what you are looking for, e.g. "user timezone
     *     and location" or "order delivery timestamp"
     * @param topK maximum number of results to return (recommended: 5–10)
     * @return formatted list of matching tables/columns with their descriptions and relevance
     *     scores
     */
    @Tool(
            name = "search_schema",
            value =
                    "Search the database schema using hybrid keyword and semantic search. "
                            + "Returns the most relevant tables and columns for your question. "
                            + "Use this to find which tables/columns to query before writing SQL."
                            + "The list of tables/columns are provided in descending order of their relevance. "
                            + "Result format is as follows:"
                            + "%num. [%type] %name (score: %score)"
                            + "   %content"
                            + "where %num is the index in the list, %type is TABLE or COLUMN, %name is either table name or column name "
                            + "(column name is always table_name.column_name)"
                            + "%score is the relevance score (floating point number between 0-1), "
                            + "%content contains the natural-language description of the table/column that was indexed in the vector store. "
                            + "Parameters: query (natural-language description of what you need), "
                            + "topK (max results, default 8).")
    public String searchSchema(String query, int topK) {
        if (topK <= 0) {
            topK = 8;
        }
        try {
            List<SchemaSearchResult> results = vectorStore.hybridSearch(query, topK);
            if (results.isEmpty()) {
                return "No schema matches found for: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Schema search results for: \"").append(query).append("\"\n\n");

            for (int i = 0; i < results.size(); i++) {
                SchemaSearchResult r = results.get(i);
                sb.append(String.format(
                        "%d. [%s] %s%s (score: %.3f)%n   %s%n%n",
                        i + 1,
                        r.docType().toUpperCase(),
                        r.tableName(),
                        r.columnName() != null ? "." + r.columnName() : "",
                        r.score(),
                        r.content()));
            }
            return sb.toString();
        } catch (IOException e) {
            log.warn("Schema vector search failed for query '{}': {}", query, e.getMessage());
            return "Schema search failed: " + e.getMessage();
        }
    }

    /**
     * Returns the full semantic description of one or more tables from
     * {@code schema_descriptions.json}, including all column names, data types, nullability, and
     * natural-language descriptions.
     *
     * <p>Call this after {@code search_schema} to get structured details for the tables identified
     * as relevant to the user's question.
     *
     * @param tableDescRequest An object of type {@code TableDescRequest} which
     *                         contains the list of table names to describe (e.g. ["orders", "users"])
     * @return formatted description for each requested table with its columns
     */
    @Tool(
            name = "get_table_desc",
            value =
                    "Get the full description of one or more tables from schema_descriptions.json. "
                            + "Returns column names, data types, nullability, and semantic descriptions. "
                            + "Call this after search_schema to understand the tables relevant to the query. "
                            + "Parameters: tableNames (list of table names, e.g. [\"orders\", \"users\"]).")
    public String getTableDescription(TableDescRequest tableDescRequest) {
        StringBuilder sb = new StringBuilder();
        List<String> tableNames = tableDescRequest.tableNames();
        if (tableNames == null) {
            return "No results. No table names provided in input";
        }
        for (String tableName : tableNames) {
            JsonNode tableNode = schemaDescriptions.get(tableName);
            if (tableNode == null) {
                sb.append("## Table: ").append(tableName).append("\nNot found.\n\n");
                continue;
            }
            sb.append("## Table: ").append(tableName).append("\n");
            sb.append(tableNode.get("description").asText()).append("\n\n");

            JsonNode pks = tableNode.get("primaryKeyColumns");
            if (pks != null && pks.isArray()) {
                sb.append("Primary key: ");
                pks.forEach(pk -> sb.append(pk.asText()).append(" "));
                sb.append("\n");
            }

            JsonNode columns = tableNode.get("columns");
            if (columns != null && columns.isArray()) {
                sb.append("\n**Columns:**\n");
                for (JsonNode col : columns) {
                    sb.append(
                            String.format(
                                    "- `%s` (%s%s): %s%n",
                                    col.get("name").asText(),
                                    col.get("dataType").asText(),
                                    col.get("nullable").asBoolean() ? ", nullable" : ", not null",
                                    col.get("description").asText()));
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * Returns the description of a specific column from {@code schema_descriptions.json}.
     *
     * @param tableName name of the table containing the column
     * @param columnName name of the column to describe
     * @return data type, nullability, and semantic description of the column
     */
    @Tool(
            name = "get_column_desc",
            value =
                    "Get the description of a specific column from schema_descriptions.json. "
                            + "Returns the data type, nullability, and semantic meaning of the column. "
                            + "Parameters: tableName (table name), columnName (column name).")
    public String getColumnDescription(String tableName, String columnName) {
        JsonNode tableNode = schemaDescriptions.get(tableName);
        if (tableNode == null) {
            return "Table not found: " + tableName;
        }
        JsonNode columns = tableNode.get("columns");
        if (columns != null && columns.isArray()) {
            for (JsonNode col : columns) {
                if (columnName.equals(col.get("name").asText())) {
                    return String.format(
                            "Column `%s.%s` (%s%s): %s",
                            tableName,
                            columnName,
                            col.get("dataType").asText(),
                            col.get("nullable").asBoolean() ? ", nullable" : ", not null",
                            col.get("description").asText());
                }
            }
        }
        return "Column not found: " + tableName + "." + columnName;
    }

    // -------------------------------------------------------------------------
    // Result formatting
    // -------------------------------------------------------------------------

    /**
     * Get a summary of row counts for all tables in the database. Use this to understand the scale
     * of data before deciding on query strategy.
     *
     * @return table names with their row counts
     */
    @Tool(
            name = "get_table_row_counts",
            value =
                    "Get the row count for every table in the e-commerce database. "
                            + "Use this to understand data volume before running complex aggregation queries.")
    @SneakyThrows
    public String getTableRowCounts() {
        try (Connection conn = connect()) {
            final var sb = new StringBuilder("## Row counts per table\n\n");
            for (final String table : getTables(conn)) {
                try (var stmt = conn.createStatement();
                        ResultSet rs =
                                stmt.executeQuery(
                                        "SELECT COUNT(*) AS cnt FROM \"" + table + "\"")) {
                    if (rs.next()) {
                        sb.append(String.format("- %-12s : %d rows%n", table, rs.getLong("cnt")));
                    }
                }
            }
            return sb.toString();
        }
    }

    /**
     * Renders the rows from a {@link SqlQueryResult} as an ASCII table for clear display in the
     * CLI. Use this on query results before including them in the final explanation.
     *
     * @param sqlQueryResult the result produced by the SQL execution step
     */
    @Tool(
            name = "format_results_as_table",
            value =
                    "Display query result rows from a SqlQueryResult into a clean ASCII table for display. "
                            + "Pass the SqlQueryResult returned by the agent's SQL execution. "
                            + "Returns a formatted ASCII table string.")
    public static String formatResultsAsTable(SqlQueryResult sqlQueryResult) {
        try {
            final List<String> jsonRows = sqlQueryResult.results();

            if (jsonRows == null || jsonRows.isEmpty()) {
                return "No results found.";
            }

            final TypeReference<LinkedHashMap<String, Object>> rowType = new TypeReference<>() {};
            final List<Map<String, Object>> rows = new ArrayList<>(jsonRows.size());
            for (final String jsonRow : jsonRows) {
                rows.add(MAPPER.readValue(jsonRow, rowType));
            }

            final List<String> headers = new ArrayList<>(rows.get(0).keySet());

            final AsciiTable at = new AsciiTable();
            at.getRenderer().setCWC(new CWC_LongestLine());
            at.addRule();
            at.addRow(headers.toArray());
            at.addRule();
            for (final var row : rows) {
                final Object[] cells =
                        headers.stream()
                                .map(
                                        h ->
                                                row.getOrDefault(h, "") == null
                                                        ? "NULL"
                                                        : String.valueOf(row.get(h)))
                                .toArray();
                at.addRow(cells);
                at.addRule();
            }
            at.setTextAlignment(TextAlignment.LEFT);

            return at.render();
        } catch (Exception e) {
            log.warn("Failed to format results as table: {}", e.getMessage());
            return "Could not format results: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    @SneakyThrows
    private void appendTableDdl(Connection conn, String table, StringBuilder sb) {
        // Get CREATE TABLE statement for the full annotated DDL
        try (var stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT sql FROM sqlite_master WHERE type='table' AND name='"
                                        + table
                                        + "'")) {
            if (rs.next()) {
                sb.append("```sql\n").append(rs.getString("sql")).append("\n```\n");
            }
        }

        // Append pragma table_info for quick column reference
        sb.append("\n**Columns:**\n");
        try (var stmt = conn.prepareStatement("PRAGMA table_info(\"" + table + "\")");
                ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                final String colName = rs.getString("name");
                final String colType = rs.getString("type");
                final String notNull =
                        "1".equals(String.valueOf(rs.getObject("notnull"))) ? " NOT NULL" : "";
                final Object dflt = rs.getObject("dflt_value");
                final String dfltStr = dflt != null ? " DEFAULT " + dflt : "";
                final String pk = "1".equals(String.valueOf(rs.getObject("pk"))) ? " [PK]" : "";
                sb.append(
                        String.format("- `%s` %s%s%s%s%n", colName, colType, notNull, dfltStr, pk));
            }
        }
    }

    @SneakyThrows
    private Connection connect() {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    @SneakyThrows
    private List<String> getTables(Connection conn) {
        final List<String> tables = new ArrayList<>();
        try (var stmt = conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
        }
        return tables;
    }
}
