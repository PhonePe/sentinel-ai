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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletSseServerTransportProvider;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Standalone MCP server that wraps SQLite operations over stdio or HTTP SSE.
 *
 * <p>This server is launched as a subprocess by {@code TextToSqlCLI} when {@code --toolbox-mode
 * mcp} is selected. It communicates via the MCP protocol and exposes four tools:
 *
 * <ul>
 *   <li>{@code execute_query} – Execute a read-only SELECT statement
 *   <li>{@code list_tables} – List all user-defined tables
 *   <li>{@code get_table_schema} – Get column definitions for a table
 *   <li>{@code get_database_info} – Return database metadata
 * </ul>
 *
 * <p>Two transport modes are supported via {@code --transport}:
 *
 * <ul>
 *   <li>{@code STDIO} (default) – reads/writes MCP messages via stdin/stdout. All log output is
 *       redirected to {@code stderr} so it does not corrupt the MCP protocol messages.
 *   <li>{@code SSE} – serves MCP over HTTP Server-Sent Events on the port specified by {@code
 *       --port} (default: {@value #DEFAULT_SSE_PORT}).
 * </ul>
 */
@Slf4j
@Command(
        name = "sqlite-mcp-server",
        mixinStandardHelpOptions = true,
        description = "MCP server exposing SQLite operations over stdio or SSE")
public class SqliteMcpServer implements Callable<Integer> {

    /** Default port used when the server runs in SSE transport mode. */
    public static final int DEFAULT_SSE_PORT = 8766;

    /**
     * Transport mode for the MCP server.
     *
     * <ul>
     *   <li>{@code STDIO} – reads/writes MCP messages via stdin/stdout (default)
     *   <li>{@code SSE} – serves MCP over HTTP Server-Sent Events on a fixed port
     * </ul>
     */
    public enum TransportMode {
        STDIO,
        SSE
    }

    @Option(
            names = {"--db-path"},
            required = true,
            description = "Absolute path to the SQLite database file")
    private String dbPath;

    @Option(
            names = {"--transport", "-T"},
            description =
                    "Transport mode for the MCP server: ${COMPLETION-CANDIDATES}. Default: STDIO",
            defaultValue = "STDIO")
    private TransportMode transport;

    @Option(
            names = {"--port", "-p"},
            description =
                    "HTTP port to which the MCP server should bind when using --transport=SSE (default: " + DEFAULT_SSE_PORT + ")",
            defaultValue = "" + DEFAULT_SSE_PORT)
    private int port;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        // Redirect all logging to stderr — stdout is reserved for the MCP protocol.
        redirectLoggingToStderr();
        System.exit(new CommandLine(new SqliteMcpServer()).execute(args));
    }

    // -------------------------------------------------------------------------
    // Callable implementation
    // -------------------------------------------------------------------------

    @Override
    @SneakyThrows
    public Integer call() {
        DatabaseInitializer.ensureInitialised(Paths.get(dbPath).toAbsolutePath());
        log.info("SQLite MCP server starting [transport={}, dbPath={}]", transport, dbPath);
        return switch (transport) {
            case STDIO -> runStdioMode();
            case SSE -> runSseMode();
        };
    }

    // -------------------------------------------------------------------------
    // Transport-specific runners
    // -------------------------------------------------------------------------

    /**
     * Runs the MCP server over stdin/stdout (STDIO transport).
     *
     * <p>Blocks the main thread until the parent process closes the pipe.
     */
    @SneakyThrows
    private Integer runStdioMode() {
        final ObjectMapper mapper = JsonUtils.createMapper();
        final var jsonMapper = new JacksonMcpJsonMapper(mapper);

        // stdio transport: reads MCP requests from System.in, writes responses to System.out
        final var transportProvider = new StdioServerTransportProvider(jsonMapper);

        final McpSyncServer server =
                McpServer.sync(transportProvider)
                        .serverInfo("sqlite-mcp-server", "1.0.0")
                        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                        .toolCall(
                                executeQueryTool(jsonMapper),
                                (exchange, args) -> handleExecuteQuery(args.arguments(), mapper))
                        .toolCall(
                                listTablesTool(jsonMapper),
                                (exchange, args) -> handleListTables(mapper))
                        .toolCall(
                                getTableSchemaTool(jsonMapper),
                                (exchange, args) -> handleGetTableSchema(args.arguments(), mapper))
                        .toolCall(
                                getDatabaseInfoTool(jsonMapper),
                                (exchange, args) -> handleGetDatabaseInfo(mapper))
                        .build();

        log.info("SQLite MCP server started in STDIO mode. Database: {}", dbPath);

        // Block the main thread while the reactive pipeline processes stdin/stdout.
        // The server will be terminated when the parent process closes the pipe.
        new CountDownLatch(1).await();
        server.close();
        return 0;
    }

    /**
     * Runs the MCP server over HTTP Server-Sent Events (SSE transport).
     *
     * <p>Starts an embedded Jetty server on {@link #port} and blocks until the server is stopped.
     */
    @SneakyThrows
    private Integer runSseMode() {
        final ObjectMapper mapper = JsonUtils.createMapper();
        final var jsonMapper = new JacksonMcpJsonMapper(mapper);

        final var transportProvider =
                HttpServletSseServerTransportProvider.builder()
                        .jsonMapper(jsonMapper)
                        .baseUrl("http://localhost:" + port)
                        .messageEndpoint("/sse")
                        .build();

        final McpSyncServer server =
                McpServer.sync(transportProvider)
                        .serverInfo("sqlite-mcp-server", "1.0.0")
                        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                        .tool(
                                executeQueryTool(jsonMapper),
                                (exchange, args) -> handleExecuteQuery(args, mapper))
                        .tool(
                                listTablesTool(jsonMapper),
                                (exchange, args) -> handleListTables(mapper))
                        .tool(
                                getTableSchemaTool(jsonMapper),
                                (exchange, args) -> handleGetTableSchema(args, mapper))
                        .tool(
                                getDatabaseInfoTool(jsonMapper),
                                (exchange, args) -> handleGetDatabaseInfo(mapper))
                        .build();

        // Embed the SSE transport provider in a Jetty 12 (ee10) server
        final var jettyServer = new Server(port);
        final var context = new ServletContextHandler("/");
        context.addServlet(new ServletHolder(transportProvider), "/*");
        jettyServer.setHandler(context);
        jettyServer.start();

        log.info(
                "SQLite MCP server started in SSE transport mode on http://localhost:{}/sse. Database: {}",
                port,
                dbPath);

        // add a shutdown hook that will stop the server when the JVM is terminated
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                log.info("Shutting down SQLite MCP server...");
                jettyServer.stop();
                server.close();
            } catch (Exception e) {
                log.error("Error during shutdown: {}", e.getMessage());
            }
        }));

        // block until Jetty is stopped
        jettyServer.join();
        server.close();
        return 0;
    }

    // -------------------------------------------------------------------------
    // Tool definitions
    // -------------------------------------------------------------------------

    private McpSchema.Tool executeQueryTool(JacksonMcpJsonMapper jsonMapper) {
        return McpSchema.Tool.builder()
                .name("execute_query")
                .description(
                        "Execute a read-only SQL SELECT statement against the SQLite database. "
                                + "Returns a JSON object with 'rows' (array of JSON row strings), "
                                + "'rowCount', and 'executionTimeMs'. "
                                + "Only SELECT, WITH, and PRAGMA statements are permitted.")
                .inputSchema(
                        jsonMapper,
                        """
                        {
                          "type": "object",
                          "properties": {
                            "sql": {
                              "type": "string",
                              "description": "The SQL SELECT statement to execute"
                            },
                            "values": {
                              "type": "array",
                              "description": "Optional list of parameter values for '?' placeholders",
                              "items": {}
                            }
                          },
                          "required": ["sql"]
                        }
                        """)
                .build();
    }

    private McpSchema.Tool listTablesTool(JacksonMcpJsonMapper jsonMapper) {
        return McpSchema.Tool.builder()
                .name("list_tables")
                .description("List all user-defined tables in the SQLite database.")
                .inputSchema(jsonMapper, "{\"type\":\"object\",\"properties\":{}}")
                .build();
    }

    private McpSchema.Tool getTableSchemaTool(JacksonMcpJsonMapper jsonMapper) {
        return McpSchema.Tool.builder()
                .name("get_table_schema")
                .description(
                        "Get column definitions (name, type, nullable, default) for a specific table.")
                .inputSchema(
                        jsonMapper,
                        """
                        {
                          "type": "object",
                          "properties": {
                            "tableName": {
                              "type": "string",
                              "description": "Name of the table whose schema to retrieve"
                            }
                          },
                          "required": ["tableName"]
                        }
                        """)
                .build();
    }

    private McpSchema.Tool getDatabaseInfoTool(JacksonMcpJsonMapper jsonMapper) {
        return McpSchema.Tool.builder()
                .name("get_database_info")
                .description(
                        "Return high-level metadata about the SQLite database: "
                                + "path, table count, table names, and approximate size in bytes.")
                .inputSchema(jsonMapper, "{\"type\":\"object\",\"properties\":{}}")
                .build();
    }

    // -------------------------------------------------------------------------
    // Tool handlers
    // -------------------------------------------------------------------------

    @SneakyThrows
    private McpSchema.CallToolResult handleExecuteQuery(
            Map<String, Object> args, ObjectMapper mapper) {
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

    @SneakyThrows
    private McpSchema.CallToolResult handleListTables(ObjectMapper mapper) {
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

    @SneakyThrows
    private McpSchema.CallToolResult handleGetTableSchema(
            Map<String, Object> args, ObjectMapper mapper) {
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

    @SneakyThrows
    private McpSchema.CallToolResult handleGetDatabaseInfo(ObjectMapper mapper) {
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

    private McpSchema.CallToolResult success(String text) {
        return new McpSchema.CallToolResult(text, false);
    }

    private McpSchema.CallToolResult error(String message) {
        return new McpSchema.CallToolResult(message, true);
    }

    // -------------------------------------------------------------------------
    // Logging configuration
    // -------------------------------------------------------------------------

    /**
     * Reconfigures Logback to write all log output to {@code stderr}.
     *
     * <p>This is required because the MCP stdio protocol uses {@code stdout} for message exchange;
     * any log lines written to {@code stdout} would corrupt the JSON-RPC framing and break the
     * connection.
     */
    private static void redirectLoggingToStderr() {
        final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        final ch.qos.logback.classic.Logger root =
                context.getLogger(Logger.ROOT_LOGGER_NAME);
        root.detachAndStopAllAppenders();

        final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(context);
        encoder.setPattern("%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} — %msg%n");
        encoder.start();

        final ConsoleAppender<ILoggingEvent> appender = new ConsoleAppender<>();
        appender.setContext(context);
        appender.setTarget("System.err");
        appender.setEncoder(encoder);
        appender.start();

        root.addAppender(appender);
        root.setLevel(Level.INFO);
    }
}
