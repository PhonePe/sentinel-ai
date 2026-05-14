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

import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SqliteMcpServer}.
 *
 * <p>The STDIO and SSE transport modes require a blocking subprocess and embedded Jetty/Reactor
 * server — these are integration concerns covered by end-to-end tests. Here we focus on:
 *
 * <ul>
 * <li>Constants and enum values
 * <li>Tool handler methods (accessed via reflection because they are package-private)
 * <li>Error / validation branches in each handler
 * </ul>
 */
@DisplayName("SqliteMcpServer")
class SqliteMcpServerTest {

    @TempDir
    static Path tempDir;

    static SqliteMcpServer server;
    static SqliteMcpServer badServer;
    static ObjectMapper mapper;

    @Nested
    @DisplayName("handleExecuteQuery — additional cases")
    class HandleExecuteQueryAdditionalTests {

        @ParameterizedTest
        @CsvSource({
                "server,SELECT FROM INVALID SYNTAX !!!",
                "badServer,SELECT 1"
        })
        @DisplayName("returns error for failing queries")
        void returnsErrorForFailingQueries(String target, String sql) throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleExecuteQuery",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var selected = "badServer".equals(target) ? badServer : server;
            final var result = (McpSchema.CallToolResult) method.invoke(selected,
                                                                        Map.of("sql", sql),
                                                                        mapper);
            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.isError(), "Failing query should return error");
        }
    }

    @Nested
    @DisplayName("handleExecuteQuery")
    class HandleExecuteQueryTests {

        @Test
        @DisplayName("executes query with explicit empty values list")
        void executesQueryWithEmptyValues() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleExecuteQuery",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(
                                                                        server,
                                                                        Map.of("sql",
                                                                               "SELECT COUNT(*) FROM users",
                                                                               "values",
                                                                               List.of()),
                                                                        mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError(), "SELECT with empty values list should succeed");
        }

        @Test
        @DisplayName("executes SELECT from users table successfully")
        void executesSelectFromUsers() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleExecuteQuery",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(
                                                                        server,
                                                                        Map.of("sql",
                                                                               "SELECT user_id FROM users LIMIT 3"),
                                                                        mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError(), "SELECT from users should succeed");
            assertTrue(firstText(result).contains("rows"));
        }

        @ParameterizedTest
        @CsvSource({
                "INSERT INTO users (id) VALUES (99999),INSERT should be rejected",
                "DELETE FROM users WHERE id = 99999,DELETE should be rejected",
                "UPDATE users SET id = 0 WHERE id = 99999,UPDATE should be rejected"
        })
        @DisplayName("returns error for write statements")
        void returnsErrorForWriteStatements(String sql, String message) throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleExecuteQuery",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(server,
                                                                        Map.of("sql", sql),
                                                                        mapper);
            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.isError(), message);
        }

        @Test
        @DisplayName("returns error when sql field is missing")
        void returnsErrorWhenSqlMissing() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleExecuteQuery",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(server, Map.of(), mapper);
            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.isError(), "Missing sql should return an error");
        }

        @Test
        @DisplayName("returns rows for a valid SELECT statement")
        void returnsRowsForValidSelect() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleExecuteQuery",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(
                                                                        server,
                                                                        Map.of("sql",
                                                                               "SELECT 1 AS one"),
                                                                        mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError(), "Valid SELECT should succeed");
        }
    }

    // =========================================================================
    // Constants / enum
    // =========================================================================

    @Nested
    @DisplayName("handleGetDatabaseInfo")
    class HandleGetDatabaseInfoTests {

        @Test
        @DisplayName("returns database metadata successfully")
        void returnsDatabaseMetadata() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleGetDatabaseInfo",
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(server, mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError(), "handleGetDatabaseInfo should not return an error");
            assertTrue(firstText(result).contains("tableCount"));
        }

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleGetDatabaseInfo",
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(badServer, mapper);
            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.isError(), "Bad db path should return error");
        }
    }

    @Nested
    @DisplayName("handleGetTableSchema — additional cases")
    class HandleGetTableSchemaAdditionalTests {

        @Test
        @DisplayName("returns error for blank tableName")
        void returnsErrorForBlankTableName() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleGetTableSchema",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(server,
                                                                        Map.of("tableName", "   "),
                                                                        mapper);
            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.isError(), "Blank tableName should return an error");
        }
    }

    @Nested
    @DisplayName("handleGetTableSchema")
    class HandleGetTableSchemaTests {

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleGetTableSchema",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(badServer,
                                                                        Map.of("tableName",
                                                                               "users"),
                                                                        mapper);
            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.isError(), "Bad db path should return error");
        }

        @Test
        @DisplayName("returns error for unknown table")
        void returnsErrorForUnknownTable() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleGetTableSchema",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(
                                                                        server,
                                                                        Map.of("tableName",
                                                                               "nonexistent_xyz"),
                                                                        mapper);
            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.isError(), "Should return error for unknown table");
        }

        @Test
        @DisplayName("returns error when tableName is missing")
        void returnsErrorWhenTableNameMissing() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleGetTableSchema",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(server, Map.of(), mapper);
            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.isError(), "Should return error when tableName is missing");
        }

        @Test
        @DisplayName("returns schema for an existing table")
        void returnsSchemaForExistingTable() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleGetTableSchema",
                                                                       Map.class,
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(server,
                                                                        Map.of("tableName",
                                                                               "users"),
                                                                        mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError(), "Should succeed for existing table");
            assertTrue(firstText(result).contains("users"));
        }
    }

    // =========================================================================
    // handleListTables (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("handleListTables")
    class HandleListTablesTests {

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleListTables",
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(badServer, mapper);
            assertNotNull(result);
            assertEquals(Boolean.TRUE, result.isError(), "Bad db path should return error");
        }

        @Test
        @DisplayName("returns success result with tables list")
        void returnsTablesSuccessfully() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod(
                                                                       "handleListTables",
                                                                       ObjectMapper.class);
            method.setAccessible(true);
            final var result = (McpSchema.CallToolResult) method.invoke(server, mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError(), "handleListTables should not return an error");
            assertTrue(
                       firstText(result).contains("tables"),
                       "Result should contain a 'tables' key");
        }
    }

    // =========================================================================
    // handleGetTableSchema (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("redirectLoggingToStderr")
    class RedirectLoggingTests {

        @Test
        @DisplayName("redirectLoggingToStderr does not throw")
        void redirectLoggingToStderrDoesNotThrow() throws Exception {
            final var method = SqliteMcpServer.class.getDeclaredMethod("redirectLoggingToStderr");
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(null));
        }
    }

    // =========================================================================
    // handleGetDatabaseInfo (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("tool definitions")
    class ToolDefinitionTests {

        @ParameterizedTest
        @CsvSource({
                "executeQueryTool,execute_query",
                "listTablesTool,list_tables",
                "getTableSchemaTool,get_table_schema",
                "getDatabaseInfoTool,get_database_info"
        })
        @DisplayName("tool definition methods return tools with expected names")
        void toolDefinitionMethodsHaveCorrectNames(String methodName, String expectedName)
                throws Exception {
            final var jsonMapper = new JacksonMcpJsonMapper(mapper);
            final var method = SqliteMcpServer.class.getDeclaredMethod(methodName, JacksonMcpJsonMapper.class);
            method.setAccessible(true);
            final var tool = (McpSchema.Tool) method.invoke(server, jsonMapper);
            assertNotNull(tool);
            assertEquals(expectedName, tool.name());
            assertNotNull(tool.description());
        }
    }

    // =========================================================================
    // handleExecuteQuery (via reflection)
    // =========================================================================

    @BeforeAll
    static void setUp() throws NoSuchFieldException, IllegalAccessException {
        final var dbPath = tempDir.resolve("mcp-test.db");
        DatabaseInitializer.ensureInitialised(dbPath);

        // Build the server instance and inject the dbPath field via reflection so that the
        // handler methods can access the database.
        server = new SqliteMcpServer();
        final var dbPathField = SqliteMcpServer.class.getDeclaredField("dbPath");
        dbPathField.setAccessible(true);
        dbPathField.set(server, dbPath.toAbsolutePath().toString());

        // A server with an invalid db path — used to exercise exception/catch branches.
        badServer = new SqliteMcpServer();
        dbPathField.set(badServer, "/nonexistent/path/to/db.sqlite");

        mapper = JsonUtils.createMapper();
    }

    // =========================================================================
    // Tool definition methods (via reflection)
    // =========================================================================

    /** Extracts the text from the first content element of a {@link McpSchema.CallToolResult}. */
    private static String firstText(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }
        final var first = result.content().get(0);
        if (first instanceof TextContent tc) {
            return tc.text() != null ? tc.text() : "";
        }
        return first.toString();
    }

    // =========================================================================
    // redirectLoggingToStderr (via reflection)
    // =========================================================================

    @Test
    @DisplayName("DEFAULT_SSE_PORT is 8766")
    void defaultSsePort() {
        assertEquals(8766, SqliteMcpServer.DEFAULT_SSE_PORT);
    }

    // =========================================================================
    // handleExecuteQuery — exception path (bad SQL syntax)
    // =========================================================================

    @Test
    @DisplayName("TransportMode enum has STDIO and SSE values")
    void transportModeEnumValues() {
        final var values = SqliteMcpServer.TransportMode.values();
        assertEquals(2, values.length);
        assertEquals(SqliteMcpServer.TransportMode.STDIO, values[0]);
        assertEquals(SqliteMcpServer.TransportMode.SSE, values[1]);
    }

    // =========================================================================
    // handleGetTableSchema — additional cases
    // =========================================================================

    @Test
    @DisplayName("TransportMode valueOf works for STDIO and SSE")
    void transportModeValueOf() {
        assertEquals(
                     SqliteMcpServer.TransportMode.STDIO,
                     SqliteMcpServer.TransportMode.valueOf("STDIO"));
        assertEquals(
                     SqliteMcpServer.TransportMode.SSE,
                     SqliteMcpServer.TransportMode.valueOf("SSE"));
    }
}
