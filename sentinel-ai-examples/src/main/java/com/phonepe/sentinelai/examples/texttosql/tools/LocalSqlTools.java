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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.examples.texttosql.agent.SqlQueryResult;
import de.vandermeer.asciitable.AsciiTable;
import de.vandermeer.asciitable.CWC_LongestLine;
import de.vandermeer.skb.interfaces.transformers.textformat.TextAlignment;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class LocalSqlTools implements ToolBox {
    private static final DateTimeFormatter DISPLAY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final String dbPath;

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

    /**
     * Returns the full database schema with all table definitions, column names, types,
     * constraints, and inline comments describing the purpose of each column. This is the most
     * important tool for understanding the data model before generating SQL queries.
     *
     * <p>Always call this at the start of any SQL generation task.
     *
     * @return formatted schema description covering all tables
     */
    @Tool(
            name = "get_db_schema",
            value =
                    "Get the complete e-commerce database schema with all table and column descriptions. "
                            + "ALWAYS call this first before generating any SQL query. "
                            + "Returns column names, types, constraints, and semantic descriptions for all tables: "
                            + "users, sellers, catalog, inventory, orders.")
    @SneakyThrows
    public String getDatabaseSchema() {
        try (Connection conn = connect()) {
            final var sb = new StringBuilder();
            sb.append("# E-Commerce Database Schema\n\n");
            sb.append("All *_at columns store Unix epoch seconds. ");
            sb.append("Use convertEpochToLocalDateTime to display them.\n\n");

            final List<String> tables = getTables(conn);
            for (final String table : tables) {
                sb.append("## Table: ").append(table).append("\n");
                appendTableDdl(conn, table, sb);
                sb.append("\n");
            }

            sb.append("## Table Relationships\n");
            sb.append("- orders.user_id    → users.user_id\n");
            sb.append("- orders.product_id → catalog.product_id\n");
            sb.append("- orders.seller_id  → sellers.seller_id\n");
            sb.append("- catalog.seller_id → sellers.seller_id\n");
            sb.append("- inventory.product_id → catalog.product_id\n\n");
            sb.append("## Status values in orders.status\n");
            sb.append("pending → confirmed → shipped → delivered  (or cancelled)\n");

            return sb.toString();
        }
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

            // System.out.printf("%n%d row(s) returned.%n", rows.size());
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
            final ResultSetMetaData meta = rs.getMetaData();
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
