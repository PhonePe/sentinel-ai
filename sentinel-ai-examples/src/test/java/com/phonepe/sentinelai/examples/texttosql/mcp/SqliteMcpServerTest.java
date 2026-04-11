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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link SqliteMcpServer}.
 *
 * <p>The STDIO and SSE transport modes require a blocking subprocess and embedded Jetty/Reactor
 * server — these are integration concerns covered by end-to-end tests. Here we focus on:
 *
 * <ul>
 *   <li>Constants and enum values
 *   <li>Tool handler methods (accessed via reflection because they are package-private)
 *   <li>Error / validation branches in each handler
 * </ul>
 */
@DisplayName("SqliteMcpServer")
class SqliteMcpServerTest {

    @TempDir
    static Path tempDir;

    static SqliteMcpServer server;
    static SqliteMcpServer badServer;
    static ObjectMapper mapper;

    @BeforeAll
    static void setUp() throws Exception {
        final Path dbPath = tempDir.resolve("mcp-test.db");
        DatabaseInitializer.ensureInitialised(dbPath);

        // Build the server instance and inject the dbPath field via reflection so that the
        // handler methods can access the database.
        server = new SqliteMcpServer();
        final Field dbPathField = SqliteMcpServer.class.getDeclaredField("dbPath");
        dbPathField.setAccessible(true);
        dbPathField.set(server, dbPath.toAbsolutePath().toString());

        // A server with an invalid db path — used to exercise exception/catch branches.
        badServer = new SqliteMcpServer();
        dbPathField.set(badServer, "/nonexistent/path/to/db.sqlite");

        mapper = JsonUtils.createMapper();
    }

    /** Extracts the text from the first content element of a {@link McpSchema.CallToolResult}. */
    private static String firstText(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return "";
        }
        final McpSchema.Content first = result.content().get(0);
        if (first instanceof TextContent tc) {
            return tc.text() != null ? tc.text() : "";
        }
        return first.toString();
    }

    // =========================================================================
    // Constants / enum
    // =========================================================================

    @Test
    @DisplayName("DEFAULT_SSE_PORT is 8766")
    void defaultSsePort() {
        assertEquals(8766, SqliteMcpServer.DEFAULT_SSE_PORT);
    }

    @Test
    @DisplayName("TransportMode enum has STDIO and SSE values")
    void transportModeEnumValues() {
        final SqliteMcpServer.TransportMode[] values = SqliteMcpServer.TransportMode.values();
        assertEquals(2, values.length);
        assertEquals(SqliteMcpServer.TransportMode.STDIO, values[0]);
        assertEquals(SqliteMcpServer.TransportMode.SSE, values[1]);
    }

    @Test
    @DisplayName("TransportMode valueOf works for STDIO and SSE")
    void transportModeValueOf() {
        assertEquals(
                SqliteMcpServer.TransportMode.STDIO,
                SqliteMcpServer.TransportMode.valueOf("STDIO"));
        assertEquals(
                SqliteMcpServer.TransportMode.SSE, SqliteMcpServer.TransportMode.valueOf("SSE"));
    }

    // =========================================================================
    // handleListTables (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("handleListTables")
    class HandleListTablesTests {

        @Test
        @DisplayName("returns success result with tables list")
        void returnsTablesSuccessfully() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleListTables", ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult) method.invoke(server, mapper);
            assertNotNull(result);
            assertFalse(Boolean.TRUE.equals(result.isError()), "handleListTables should not return an error");
            assertTrue(
                    firstText(result).contains("tables"),
                    "Result should contain a 'tables' key");
        }

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleListTables", ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult) method.invoke(badServer, mapper);
            assertNotNull(result);
            assertTrue(Boolean.TRUE.equals(result.isError()), "Bad db path should return error");
        }
    }

    // =========================================================================
    // handleGetTableSchema (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("handleGetTableSchema")
    class HandleGetTableSchemaTests {

        @Test
        @DisplayName("returns schema for an existing table")
        void returnsSchemaForExistingTable() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleGetTableSchema", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(server, Map.of("tableName", "users"), mapper);
            assertNotNull(result);
            assertFalse(Boolean.TRUE.equals(result.isError()), "Should succeed for existing table");
            assertTrue(firstText(result).contains("users"));
        }

        @Test
        @DisplayName("returns error for unknown table")
        void returnsErrorForUnknownTable() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleGetTableSchema", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(
                                    server, Map.of("tableName", "nonexistent_xyz"), mapper);
            assertNotNull(result);
            assertTrue(Boolean.TRUE.equals(result.isError()), "Should return error for unknown table");
        }

        @Test
        @DisplayName("returns error when tableName is missing")
        void returnsErrorWhenTableNameMissing() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleGetTableSchema", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult) method.invoke(server, Map.of(), mapper);
            assertNotNull(result);
            assertTrue(Boolean.TRUE.equals(result.isError()), "Should return error when tableName is missing");
        }

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleGetTableSchema", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(badServer, Map.of("tableName", "users"), mapper);
            assertNotNull(result);
            assertTrue(Boolean.TRUE.equals(result.isError()), "Bad db path should return error");
        }
    }

    // =========================================================================
    // handleGetDatabaseInfo (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("handleGetDatabaseInfo")
    class HandleGetDatabaseInfoTests {

        @Test
        @DisplayName("returns database metadata successfully")
        void returnsDatabaseMetadata() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleGetDatabaseInfo", ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult) method.invoke(server, mapper);
            assertNotNull(result);
            assertFalse(Boolean.TRUE.equals(result.isError()), "handleGetDatabaseInfo should not return an error");
            assertTrue(firstText(result).contains("tableCount"));
        }

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleGetDatabaseInfo", ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult) method.invoke(badServer, mapper);
            assertNotNull(result);
            assertTrue(Boolean.TRUE.equals(result.isError()), "Bad db path should return error");
        }
    }

    // =========================================================================
    // handleExecuteQuery (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("handleExecuteQuery")
    class HandleExecuteQueryTests {

        @Test
        @DisplayName("returns rows for a valid SELECT statement")
        void returnsRowsForValidSelect() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleExecuteQuery", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(
                                    server, Map.of("sql", "SELECT 1 AS one"), mapper);
            assertNotNull(result);
            assertFalse(Boolean.TRUE.equals(result.isError()), "Valid SELECT should succeed");
        }

        @Test
        @DisplayName("returns error when sql field is missing")
        void returnsErrorWhenSqlMissing() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleExecuteQuery", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult) method.invoke(server, Map.of(), mapper);
            assertNotNull(result);
            assertTrue(Boolean.TRUE.equals(result.isError()), "Missing sql should return an error");
        }

        @Test
        @DisplayName("returns error for an INSERT statement")
        void returnsErrorForInsert() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleExecuteQuery", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(
                                    server,
                                    Map.of("sql", "INSERT INTO users (id) VALUES (99999)"),
                                    mapper);
            assertNotNull(result);
            assertTrue(Boolean.TRUE.equals(result.isError()), "INSERT should be rejected");
        }

        @Test
        @DisplayName("returns error for a DELETE statement")
        void returnsErrorForDelete() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleExecuteQuery", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(
                                    server,
                                    Map.of("sql", "DELETE FROM users WHERE id = 99999"),
                                    mapper);
            assertNotNull(result);
            assertTrue(Boolean.TRUE.equals(result.isError()), "DELETE should be rejected");
        }

        @Test
        @DisplayName("returns error for an UPDATE statement")
        void returnsErrorForUpdate() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleExecuteQuery", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(
                                    server,
                                    Map.of("sql", "UPDATE users SET id = 0 WHERE id = 99999"),
                                    mapper);
            assertNotNull(result);
            assertTrue(Boolean.TRUE.equals(result.isError()), "UPDATE should be rejected");
        }

        @Test
        @DisplayName("executes query with explicit empty values list")
        void executesQueryWithEmptyValues() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleExecuteQuery", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(
                                    server,
                                    Map.of("sql", "SELECT COUNT(*) FROM users", "values", List.of()),
                                    mapper);
            assertNotNull(result);
            assertFalse(Boolean.TRUE.equals(result.isError()), "SELECT with empty values list should succeed");
        }

        @Test
        @DisplayName("executes SELECT from users table successfully")
        void executesSelectFromUsers() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleExecuteQuery", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(
                                    server, Map.of("sql", "SELECT user_id FROM users LIMIT 3"), mapper);
            assertNotNull(result);
            assertFalse(Boolean.TRUE.equals(result.isError()), "SELECT from users should succeed");
            assertTrue(firstText(result).contains("rows"));
        }
    }

    // =========================================================================
    // Tool definition methods (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("tool definitions")
    class ToolDefinitionTests {

        @Test
        @DisplayName("executeQueryTool returns a tool with name 'execute_query'")
        void executeQueryToolHasCorrectName() throws Exception {
            final JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "executeQueryTool", JacksonMcpJsonMapper.class);
            method.setAccessible(true);
            final McpSchema.Tool tool = (McpSchema.Tool) method.invoke(server, jsonMapper);
            assertNotNull(tool);
            assertEquals("execute_query", tool.name());
            assertNotNull(tool.description());
        }

        @Test
        @DisplayName("listTablesTool returns a tool with name 'list_tables'")
        void listTablesToolHasCorrectName() throws Exception {
            final JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "listTablesTool", JacksonMcpJsonMapper.class);
            method.setAccessible(true);
            final McpSchema.Tool tool = (McpSchema.Tool) method.invoke(server, jsonMapper);
            assertNotNull(tool);
            assertEquals("list_tables", tool.name());
        }

        @Test
        @DisplayName("getTableSchemaTool returns a tool with name 'get_table_schema'")
        void getTableSchemaToolHasCorrectName() throws Exception {
            final JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "getTableSchemaTool", JacksonMcpJsonMapper.class);
            method.setAccessible(true);
            final McpSchema.Tool tool = (McpSchema.Tool) method.invoke(server, jsonMapper);
            assertNotNull(tool);
            assertEquals("get_table_schema", tool.name());
        }

        @Test
        @DisplayName("getDatabaseInfoTool returns a tool with name 'get_database_info'")
        void getDatabaseInfoToolHasCorrectName() throws Exception {
            final JacksonMcpJsonMapper jsonMapper = new JacksonMcpJsonMapper(mapper);
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "getDatabaseInfoTool", JacksonMcpJsonMapper.class);
            method.setAccessible(true);
            final McpSchema.Tool tool = (McpSchema.Tool) method.invoke(server, jsonMapper);
            assertNotNull(tool);
            assertEquals("get_database_info", tool.name());
        }
    }

    // =========================================================================
    // redirectLoggingToStderr (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("redirectLoggingToStderr")
    class RedirectLoggingTests {

        @Test
        @DisplayName("redirectLoggingToStderr does not throw")
        void redirectLoggingToStderrDoesNotThrow() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod("redirectLoggingToStderr");
            method.setAccessible(true);
            assertDoesNotThrow(() -> method.invoke(null));
        }
    }

    // =========================================================================
    // handleExecuteQuery — exception path (bad SQL syntax)
    // =========================================================================

    @Nested
    @DisplayName("handleExecuteQuery — additional cases")
    class HandleExecuteQueryAdditionalTests {

        @Test
        @DisplayName("returns error for invalid SQL syntax")
        void returnsErrorForBadSql() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleExecuteQuery", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(
                                    server, Map.of("sql", "SELECT FROM INVALID SYNTAX !!!"), mapper);
            assertNotNull(result);
            assertTrue(
                    Boolean.TRUE.equals(result.isError()),
                    "Invalid SQL syntax should return error");
        }

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleExecuteQuery", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(
                                    badServer, Map.of("sql", "SELECT 1"), mapper);
            assertNotNull(result);
            assertTrue(
                    Boolean.TRUE.equals(result.isError()),
                    "Bad db path should return error");
        }
    }

    // =========================================================================
    // handleGetTableSchema — additional cases
    // =========================================================================

    @Nested
    @DisplayName("handleGetTableSchema — additional cases")
    class HandleGetTableSchemaAdditionalTests {

        @Test
        @DisplayName("returns error for blank tableName")
        void returnsErrorForBlankTableName() throws Exception {
            final Method method =
                    SqliteMcpServer.class.getDeclaredMethod(
                            "handleGetTableSchema", Map.class, ObjectMapper.class);
            method.setAccessible(true);
            final McpSchema.CallToolResult result =
                    (McpSchema.CallToolResult)
                            method.invoke(server, Map.of("tableName", "   "), mapper);
            assertNotNull(result);
            assertTrue(
                    Boolean.TRUE.equals(result.isError()),
                    "Blank tableName should return an error");
        }
    }
}
