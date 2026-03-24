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

package com.phonepe.sentinelai.examples.texttosql.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
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
 * JAX-RS resource that provides a REST interface to a SQLite database.
 *
 * <p>This mirrors the tools exposed by the {@code mcp-sqlite} MCP server, giving the remote-HTTP
 * toolbox an alternative path for SQL operations.
 *
 * <h2>Endpoints</h2>
 *
 * <pre>
 * POST /api/sqlite/query Execute raw SQL
 * GET /api/sqlite/tables List all tables
 * GET /api/sqlite/schema/{tableName} Get column definitions for a table
 * GET /api/sqlite/info Database file info
 * GET /api/sqlite/records/{table} Read records (filter via query params)
 * POST /api/sqlite/records/{table} Insert a record
 * PUT /api/sqlite/records/{table} Update records matching a filter
 * DELETE /api/sqlite/records/{table} Delete records matching a filter
 * </pre>
 */
@Slf4j
@Path("/api/sqlite")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class SqliteRestResource {

    private final String dbPath;
    private final ObjectMapper mapper;

    // -------------------------------------------------------------------------
    // Query endpoint — executes arbitrary SQL
    // -------------------------------------------------------------------------

    /**
     * Insert a new record into a table. Request body: {@code {"data": {"col1": val1, "col2":
     * val2}}}
     */
    @POST
    @Path("/records/{table}")
    @SneakyThrows
    public Response createRecord(@PathParam("table") String table, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> data = (Map<String, Object>) body.get("data");
        if (data == null || data.isEmpty()) {
            return error(400, "Field 'data' (non-empty object) is required");
        }

        final var cols = new ArrayList<>(data.keySet());
        final var placeholders = cols.stream().map(c -> "?").toList();
        final String sql =
                "INSERT INTO \""
                        + table
                        + "\" (\""
                        + String.join("\", \"", cols)
                        + "\") VALUES ("
                        + String.join(", ", placeholders)
                        + ")";
        final List<Object> params = cols.stream().map(data::get).toList();

        try (Connection conn = connect()) {
            final int affected = executeDml(conn, sql, params);
            return ok(Map.of("affectedRows", affected));
        } catch (Exception e) {
            return error(500, "Failed to create record: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Introspection endpoints
    // -------------------------------------------------------------------------

    /**
     * Delete records from a table matching the given conditions. Request body: {@code
     * {"conditions": {"col": val}}}
     */
    @DELETE
    @Path("/records/{table}")
    @SneakyThrows
    public Response deleteRecords(@PathParam("table") String table, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> conditions = (Map<String, Object>) body.get("conditions");
        if (conditions == null || conditions.isEmpty()) {
            return error(
                    400, "Field 'conditions' is required (to avoid accidental full-table deletes)");
        }

        final var params = new ArrayList<Object>();
        final var whereClauses = new ArrayList<String>();
        conditions.forEach(
                (k, v) -> {
                    whereClauses.add("\"" + k + "\" = ?");
                    params.add(v);
                });

        final String sql =
                "DELETE FROM \"" + table + "\" WHERE " + String.join(" AND ", whereClauses);

        try (Connection conn = connect()) {
            final int affected = executeDml(conn, sql, params);
            return ok(Map.of("affectedRows", affected));
        } catch (Exception e) {
            return error(500, "Failed to delete records: " + e.getMessage());
        }
    }

    /**
     * Execute a raw SQL SELECT statement against the database.
     *
     * <p>Request body: {@code {"sql": "SELECT ...", "values": [...]}}
     *
     * <p>Only SELECT, WITH, and PRAGMA statements are allowed. DML write statements (INSERT,
     * UPDATE, DELETE, DROP, ALTER, CREATE, REPLACE, TRUNCATE, MERGE) are rejected with a {@link
     * WriteQueryNotAllowedException}.
     *
     * <p>Returns a {@link SqlQueryResult} containing the generated SQL, the rows returned, a null
     * explanation (to be filled by the LLM), and the execution time.
     */
    @POST
    @Path("/query")
    @SneakyThrows
    public Response executeQuery(Map<String, Object> body) {
        final String sql = (String) body.get("sql");
        if (sql == null || sql.isBlank()) {
            return error(400, "Field 'sql' is required");
        }

        final String trimmedUpper = sql.trim().toUpperCase();
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
            if (trimmedUpper.startsWith(prefix)) {
                throw new WriteQueryNotAllowedException(
                        "Write DML statements are not allowed via this endpoint. Offending statement starts with: "
                                + prefix);
            }
        }

        @SuppressWarnings("unchecked")
        final List<Object> values =
                body.containsKey("values") ? (List<Object>) body.get("values") : List.of();

        log.debug("Executing SQL Query: {}", sql);

        final long startMs = System.currentTimeMillis();
        try (Connection conn = connect()) {
            final var rows = executeSelect(conn, sql, values);
            final long executionTimeMs = System.currentTimeMillis() - startMs;
            final List<String> jsonRows = new ArrayList<>(rows.size());
            for (final Map<String, Object> row : rows) {
                jsonRows.add(mapper.writeValueAsString(row));
            }
            final var result = new SqlQueryResult(sql, jsonRows, null, executionTimeMs);
            return ok(result);
        } catch (WriteQueryNotAllowedException e) {
            throw e;
        } catch (Exception e) {
            log.error("SQL execution error: {}", e.getMessage());
            return error(500, "SQL execution failed: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Exception for write queries
    // -------------------------------------------------------------------------

    /** Thrown when a write DML statement is submitted to the read-only query endpoint. */
    public static class WriteQueryNotAllowedException extends RuntimeException {
        public WriteQueryNotAllowedException(String message) {
            super(message);
        }
    }

    /** Return high-level information about the database file. */
    @GET
    @Path("/info")
    @SneakyThrows
    public Response getDatabaseInfo() {
        try (Connection conn = connect()) {
            final var tables =
                    executeSelect(
                            conn,
                            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                            List.of());
            final var pageSize = executeSelect(conn, "PRAGMA page_size", List.of());
            final var pageCount = executeSelect(conn, "PRAGMA page_count", List.of());

            final long ps =
                    tables.isEmpty()
                            ? 0
                            : Long.parseLong(
                                    String.valueOf(
                                            pageSize.isEmpty()
                                                    ? 0
                                                    : pageSize.get(0).get("page_size")));
            final long pc =
                    pageCount.isEmpty()
                            ? 0
                            : Long.parseLong(String.valueOf(pageCount.get(0).get("page_count")));

            return ok(
                    Map.of(
                            "databasePath",
                            dbPath,
                            "tableCount",
                            tables.size(),
                            "tables",
                            tables.stream().map(r -> r.get("name")).toList(),
                            "approximateSizeBytes",
                            ps * pc));
        } catch (Exception e) {
            return error(500, "Failed to get db info: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // CRUD endpoints
    // -------------------------------------------------------------------------

    /** Return the column definitions for a specific table. */
    @GET
    @Path("/schema/{tableName}")
    @SneakyThrows
    public Response getTableSchema(@PathParam("tableName") String tableName) {
        try (Connection conn = connect()) {
            final var rows =
                    executeSelect(conn, "PRAGMA table_info(\"" + tableName + "\")", List.of());
            if (rows.isEmpty()) {
                return error(404, "Table not found: " + tableName);
            }
            return ok(Map.of("tableName", tableName, "columns", rows));
        } catch (Exception e) {
            return error(500, "Failed to get schema: " + e.getMessage());
        }
    }

    /** List all user-defined tables in the database. */
    @GET
    @Path("/tables")
    @SneakyThrows
    public Response listTables() {
        final String sql =
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name";
        try (Connection conn = connect()) {
            final var rows = executeSelect(conn, sql, List.of());
            final List<String> tables = rows.stream().map(r -> (String) r.get("name")).toList();
            return ok(Map.of("tables", tables));
        } catch (Exception e) {
            return error(500, "Failed to list tables: " + e.getMessage());
        }
    }

    /**
     * Read records from a table. Accepts optional {@code ?columnName=value} query parameters to
     * filter results, plus {@code ?limit} and {@code ?offset} for pagination.
     */
    @GET
    @Path("/records/{table}")
    @SneakyThrows
    public Response readRecords(
            @PathParam("table") String table,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("conditions") String conditionsJson) {

        try (Connection conn = connect()) {
            final var sb = new StringBuilder("SELECT * FROM \"").append(table).append("\"");
            final List<Object> params = new ArrayList<>();

            if (conditionsJson != null && !conditionsJson.isBlank()) {
                @SuppressWarnings("unchecked")
                final Map<String, Object> conditions = mapper.readValue(conditionsJson, Map.class);
                if (!conditions.isEmpty()) {
                    sb.append(" WHERE ");
                    final var clauses = new ArrayList<String>();
                    conditions.forEach(
                            (k, v) -> {
                                clauses.add("\"" + k + "\" = ?");
                                params.add(v);
                            });
                    sb.append(String.join(" AND ", clauses));
                }
            }

            if (limit != null) {
                sb.append(" LIMIT ").append(limit);
            }
            if (offset != null) {
                sb.append(" OFFSET ").append(offset);
            }

            final var rows = executeSelect(conn, sb.toString(), params);
            return ok(Map.of("rows", rows, "rowCount", rows.size()));
        } catch (Exception e) {
            return error(500, "Failed to read records: " + e.getMessage());
        }
    }

    /** Update records in a table. Request body: {@code {"data": {...}, "conditions": {...}}} */
    @PUT
    @Path("/records/{table}")
    @SneakyThrows
    public Response updateRecords(@PathParam("table") String table, Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        final Map<String, Object> data = (Map<String, Object>) body.get("data");
        @SuppressWarnings("unchecked")
        final Map<String, Object> conditions = (Map<String, Object>) body.get("conditions");
        if (data == null || data.isEmpty()) {
            return error(400, "Field 'data' is required");
        }
        if (conditions == null || conditions.isEmpty()) {
            return error(
                    400, "Field 'conditions' is required (to avoid accidental full-table updates)");
        }

        final var params = new ArrayList<Object>();
        final var setClauses = new ArrayList<String>();
        data.forEach(
                (k, v) -> {
                    setClauses.add("\"" + k + "\" = ?");
                    params.add(v);
                });
        final var whereClauses = new ArrayList<String>();
        conditions.forEach(
                (k, v) -> {
                    whereClauses.add("\"" + k + "\" = ?");
                    params.add(v);
                });

        final String sql =
                "UPDATE \""
                        + table
                        + "\" SET "
                        + String.join(", ", setClauses)
                        + " WHERE "
                        + String.join(" AND ", whereClauses);

        try (Connection conn = connect()) {
            final int affected = executeDml(conn, sql, params);
            return ok(Map.of("affectedRows", affected));
        } catch (Exception e) {
            return error(500, "Failed to update records: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    @SneakyThrows
    private Connection connect() {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    private Response error(int status, String message) {
        return Response.status(status)
                .entity("{\"error\":\"" + message.replace("\"", "'") + "\"}")
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    @SneakyThrows
    private int executeDml(Connection conn, String sql, List<Object> params) {
        try (final var stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }
            return stmt.executeUpdate();
        }
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

    @SneakyThrows
    private Response ok(Object payload) {
        return Response.ok(mapper.writeValueAsString(payload), MediaType.APPLICATION_JSON).build();
    }
}
