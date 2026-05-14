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

import com.phonepe.sentinelai.examples.texttosql.sql.SqlValidationUtils;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        final var sql = (String) args.get("sql");
        if (sql == null || sql.isBlank()) {
            return error("Field 'sql' is required");
        }

        final var disallowedKeyword = SqlValidationUtils.findDisallowedWriteKeyword(sql);
        if (disallowedKeyword != null) {
            return error(
                         "Write DML statements are not allowed via this endpoint. Statement starts with: "
                                 + disallowedKeyword);
        }

        @SuppressWarnings("unchecked") final var values = args.containsKey("values") ? (List<Object>) args.get(
                                                                                                               "values")
                : List.of();

        log.info("Executing SQL: {}", sql);
        final var startMs = System.currentTimeMillis();

        try (Connection conn = connect()) {
            final var rows = executeSelect(conn, sql, values);
            final var elapsed = System.currentTimeMillis() - startMs;

            final List<String> jsonRows = new ArrayList<>(rows.size());
            for (final var row : rows) {
                jsonRows.add(mapper.writeValueAsString(row));
            }

            final var result = new LinkedHashMap<>();
            result.put("sql", sql);
            result.put("rows", jsonRows);
            result.put("rowCount", rows.size());
            result.put("executionTimeMs", elapsed);
            return success(mapper.writeValueAsString(result));
        }
        catch (Exception e) {
            log.error("SQL execution error: {}", e.getMessage());
            return error("SQL execution failed: " + e.getMessage());
        }
    }

    /** Returns high-level metadata about the database (path, table count, size). */
    @SneakyThrows
    public McpSchema.CallToolResult getDatabaseInfo(ObjectMapper mapper) {
        try (Connection conn = connect()) {
            final var tables = executeSelect(
                                             conn,
                                             "SELECT name FROM sqlite_master WHERE type='table' "
                                                     + "AND name NOT LIKE 'sqlite_%'",
                                             List.of());
            final var pageSize = executeSelect(conn, "PRAGMA page_size", List.of());
            final var pageCount = executeSelect(conn, "PRAGMA page_count", List.of());

            final var ps = pageSize.isEmpty()
                    ? 0L
                    : Long.parseLong(String.valueOf(pageSize.get(0).get("page_size")));
            final var pc = pageCount.isEmpty()
                    ? 0L
                    : Long.parseLong(String.valueOf(pageCount.get(0).get("page_count")));

            final var info = new LinkedHashMap<>();
            info.put("databasePath", dbPath);
            info.put("tableCount", tables.size());
            info.put("tables", tables.stream().map(r -> r.get("name")).toList());
            info.put("approximateSizeBytes", ps * pc);
            return success(mapper.writeValueAsString(info));
        }
        catch (Exception e) {
            return error("Failed to get database info: " + e.getMessage());
        }
    }

    /** Returns the column definitions for a specific table. */
    @SneakyThrows
    public McpSchema.CallToolResult getTableSchema(Map<String, Object> args, ObjectMapper mapper) {
        final var tableName = (String) args.get("tableName");
        if (tableName == null || tableName.isBlank()) {
            return error("Field 'tableName' is required");
        }
        try {
            SqlValidationUtils.validateTableName(tableName);
        }
        catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
        try (Connection conn = connect()) {
            final var columns = executeSelect(conn,
                                              "PRAGMA table_info(\"" + tableName + "\")",
                                              List.of());
            if (columns.isEmpty()) {
                return error("Table not found: " + tableName);
            }
            return success(
                           mapper.writeValueAsString(Map.of("tableName", tableName, "columns", columns)));
        }
        catch (Exception e) {
            return error("Failed to get schema for table '" + tableName + "': " + e.getMessage());
        }
    }

    /** Lists all user-defined tables in the SQLite database. */
    @SneakyThrows
    public McpSchema.CallToolResult listTables(ObjectMapper mapper) {
        try (Connection conn = connect()) {
            final var rows = executeSelect(
                                           conn,
                                           "SELECT name FROM sqlite_master WHERE type='table' "
                                                   + "AND name NOT LIKE 'sqlite_%' ORDER BY name",
                                           List.of());
            final var tables = rows.stream().map(r -> (String) r.get("name")).toList();
            return success(mapper.writeValueAsString(Map.of("tables", tables)));
        }
        catch (Exception e) {
            return error("Failed to list tables: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // JDBC helpers
    // -------------------------------------------------------------------------

    /**
     * NOTE: This creates a new connection per call and is intentionally kept simple for demo purposes.
     * For production use, replace with a connection pool (e.g. HikariCP, C3P0, etc.).
     */
    @SneakyThrows
    private Connection connect() {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private McpSchema.CallToolResult error(String message) {
        return McpSchema.CallToolResult.builder().addTextContent(message).isError(true).build();
    }

    // -------------------------------------------------------------------------
    // Result helpers
    // -------------------------------------------------------------------------

    @SneakyThrows
    private List<Map<String, Object>> executeSelect(
                                                    Connection conn,
                                                    String sql,
                                                    List<Object> params) {
        try (final var stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                final var meta = rs.getMetaData();
                final var cols = meta.getColumnCount();
                final List<Map<String, Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    final LinkedHashMap<String, Object> row = new LinkedHashMap<>();
                    for (int c = 1; c <= cols; c++) {
                        row.put(meta.getColumnName(c), rs.getObject(c));
                    }
                    rows.add(row);
                }
                return rows;
            }
        }
    }

    private McpSchema.CallToolResult success(String text) {
        return McpSchema.CallToolResult.builder().addTextContent(text).isError(false).build();
    }
}
