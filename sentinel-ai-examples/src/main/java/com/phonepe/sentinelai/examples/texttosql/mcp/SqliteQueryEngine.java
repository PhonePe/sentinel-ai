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

package com.phonepe.sentinelai.examples.texttosql.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes SQL read operations against an SQLite database and returns {@link
 * McpSchema.CallToolResult} values suitable for use as MCP tool responses.
 *
 * <p>This class contains all of the business logic that was previously embedded as private methods
 * inside {@link SqliteMcpServer}. Extracting it here makes the query logic independently testable
 * without requiring any MCP server infrastructure.
 */
@Slf4j
@RequiredArgsConstructor
public class SqliteQueryEngine {

    private final String dbPath;

    // -------------------------------------------------------------------------
    // Public tool-handler API
    // -------------------------------------------------------------------------

    /**
     * Executes a read-only SQL SELECT statement and returns the rows as JSON.
     *
     * <p>Write statements (INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, REPLACE, TRUNCATE, MERGE)
     * are rejected with an error result.
     */
    @SneakyThrows
    public McpSchema.CallToolResult executeQuery(Map<String, Object> args, ObjectMapper mapper) {
        final String sql = (String) args.get("sql");
        if (sql == null || sql.isBlank()) {
            return error("Field 'sql' is required");
        }

        final String upper = sql.trim().toUpperCase();
        final Set<String> writePrefixes =
                Set.of(
                        "INSERT",
                        "UPDATE",
                        "DELETE",
                        "DROP",
                        "ALTER",
                        "CREATE",
                        "REPLACE",
                        "TRUNCATE",
                        "MERGE");
        for (final String prefix : writePrefixes) {
            if (upper.startsWith(prefix)) {
                return error(
                        "Write DML statements are not allowed via this endpoint. "
                                + "Statement starts with: "
                                + prefix);
            }
        }

        @SuppressWarnings("unchecked")
        final List<Object> values =
                args.containsKey("values") ? (List<Object>) args.get("values") : List.of();

        log.info("Executing SQL: {}", sql);
        final long startMs = System.currentTimeMillis();
        try (Connection conn = connect()) {
            final List<Map<String, Object>> rows = executeSelect(conn, sql, values);
            final long elapsed = System.currentTimeMillis() - startMs;

            final List<String> jsonRows = new ArrayList<>(rows.size());
            for (final Map<String, Object> row : rows) {
                jsonRows.add(mapper.writeValueAsString(row));
            }

            final Map<String, Object> result = new LinkedHashMap<>();
            result.put("sql", sql);
            result.put("rows", jsonRows);
            result.put("rowCount", rows.size());
            result.put("executionTimeMs", elapsed);
            return success(mapper.writeValueAsString(result));
        } catch (Exception e) {
            log.error("SQL execution error: {}", e.getMessage());
            return error("SQL execution failed: " + e.getMessage());
        }
    }

    /** Lists all user-defined tables in the SQLite database. */
    @SneakyThrows
    public McpSchema.CallToolResult listTables(ObjectMapper mapper) {
        try (Connection conn = connect()) {
            final List<Map<String, Object>> rows =
                    executeSelect(
                            conn,
                            "SELECT name FROM sqlite_master WHERE type='table' "
                                    + "AND name NOT LIKE 'sqlite_%' ORDER BY name",
                            List.of());
            final List<String> tables = rows.stream().map(r -> (String) r.get("name")).toList();
            return success(mapper.writeValueAsString(Map.of("tables", tables)));
        } catch (Exception e) {
            return error("Failed to list tables: " + e.getMessage());
        }
    }

    /** Returns the column definitions for a specific table. */
    @SneakyThrows
    public McpSchema.CallToolResult getTableSchema(Map<String, Object> args, ObjectMapper mapper) {
        final String tableName = (String) args.get("tableName");
        if (tableName == null || tableName.isBlank()) {
            return error("Field 'tableName' is required");
        }
        try (Connection conn = connect()) {
            final List<Map<String, Object>> columns =
                    executeSelect(conn, "PRAGMA table_info(\"" + tableName + "\")", List.of());
            if (columns.isEmpty()) {
                return error("Table not found: " + tableName);
            }
            return success(
                    mapper.writeValueAsString(Map.of("tableName", tableName, "columns", columns)));
        } catch (Exception e) {
            return error("Failed to get schema for table '" + tableName + "': " + e.getMessage());
        }
    }

    /** Returns high-level metadata about the database (path, table count, size). */
    @SneakyThrows
    public McpSchema.CallToolResult getDatabaseInfo(ObjectMapper mapper) {
        try (Connection conn = connect()) {
            final List<Map<String, Object>> tables =
                    executeSelect(
                            conn,
                            "SELECT name FROM sqlite_master WHERE type='table' "
                                    + "AND name NOT LIKE 'sqlite_%'",
                            List.of());
            final List<Map<String, Object>> pageSize =
                    executeSelect(conn, "PRAGMA page_size", List.of());
            final List<Map<String, Object>> pageCount =
                    executeSelect(conn, "PRAGMA page_count", List.of());

            final long ps =
                    pageSize.isEmpty()
                            ? 0L
                            : Long.parseLong(String.valueOf(pageSize.get(0).get("page_size")));
            final long pc =
                    pageCount.isEmpty()
                            ? 0L
                            : Long.parseLong(String.valueOf(pageCount.get(0).get("page_count")));

            final Map<String, Object> info = new LinkedHashMap<>();
            info.put("databasePath", dbPath);
            info.put("tableCount", tables.size());
            info.put("tables", tables.stream().map(r -> r.get("name")).toList());
            info.put("approximateSizeBytes", ps * pc);
            return success(mapper.writeValueAsString(info));
        } catch (Exception e) {
            return error("Failed to get database info: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // JDBC helpers
    // -------------------------------------------------------------------------

    @SneakyThrows
    private Connection connect() {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    @SneakyThrows
    private List<Map<String, Object>> executeSelect(
            Connection conn, String sql, List<Object> params) {
        try (final var stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                final ResultSetMetaData meta = rs.getMetaData();
                final int cols = meta.getColumnCount();
                final List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    final Map<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= cols; c++) {
                        row.put(meta.getColumnName(c), rs.getObject(c));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Result helpers
    // -------------------------------------------------------------------------

    private McpSchema.CallToolResult success(String text) {
        return new McpSchema.CallToolResult(text, false);
    }

    private McpSchema.CallToolResult error(String message) {
        return new McpSchema.CallToolResult(message, true);
    }
}
