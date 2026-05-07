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
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link SqliteQueryEngine}.
 *
 * <p>All handler methods are public, so no reflection is required.
 */
@DisplayName("SqliteQueryEngine")
class SqliteQueryEngineTest {

    @TempDir
    static Path tempDir;

    static SqliteQueryEngine engine;
    static SqliteQueryEngine badEngine;
    static ObjectMapper mapper;

    @BeforeAll
    static void setUp() {
        final Path dbPath = tempDir.resolve("query-engine-test.db");
        DatabaseInitializer.ensureInitialised(dbPath);

        engine = new SqliteQueryEngine(dbPath.toAbsolutePath().toString());
        badEngine = new SqliteQueryEngine("/nonexistent/path/to/db.sqlite");

        mapper = JsonUtils.createMapper();
    }

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
    // executeQuery
    // =========================================================================

    @Nested
    @DisplayName("executeQuery")
    class ExecuteQueryTests {

        @Test
        @DisplayName("returns rows for a valid SELECT statement")
        void returnsRowsForValidSelect() {
            final McpSchema.CallToolResult result =
                    engine.executeQuery(Map.of("sql", "SELECT 1 AS one"), mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError(), "Valid SELECT should succeed");
            assertTrue(firstText(result).contains("rows"));
        }

        @Test
        @DisplayName("returns rows for a SELECT from the users table")
        void returnsRowsFromUsersTable() {
            final McpSchema.CallToolResult result =
                    engine.executeQuery(Map.of("sql", "SELECT user_id FROM users LIMIT 3"), mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError(), "SELECT from users should succeed");
            assertTrue(firstText(result).contains("rows"));
        }

        @Test
        @DisplayName("returns error when sql field is absent")
        void returnsErrorWhenSqlAbsent() {
            final McpSchema.CallToolResult result = engine.executeQuery(Map.of(), mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("'sql' is required"));
        }

        @Test
        @DisplayName("returns error when sql field is blank")
        void returnsErrorWhenSqlBlank() {
            final McpSchema.CallToolResult result =
                    engine.executeQuery(Map.of("sql", "   "), mapper);
            assertEquals(Boolean.TRUE, result.isError());
        }

        @ParameterizedTest
        @CsvSource({
                "INSERT INTO users (id) VALUES (99999)",
                "DELETE FROM users WHERE id = 99999",
                "UPDATE users SET id = 0 WHERE id = 99999",
                "DROP TABLE users",
                "CREATE TABLE foo (id INT)",
                "ALTER TABLE users ADD COLUMN foo TEXT",
                "TRUNCATE TABLE users",
                "MERGE INTO users USING src ON x = y",
                "REPLACE INTO users (id) VALUES (1)",
                "SELECT FROM INVALID SYNTAX !!!"
        })
        @DisplayName("rejects invalid SQL statements")
        void rejectsInvalidStatements(String sql) {
            final McpSchema.CallToolResult result =
                    engine.executeQuery(Map.of("sql", sql), mapper);
            assertEquals(Boolean.TRUE, result.isError());
            if (sql.startsWith("INSERT")) {
                assertTrue(firstText(result).contains("INSERT"));
            }
        }

        @Test
        @DisplayName("executes with explicit empty values list")
        void executesWithEmptyValuesList() {
            final McpSchema.CallToolResult result =
                    engine.executeQuery(
                            Map.of("sql", "SELECT COUNT(*) FROM users", "values", List.of()),
                            mapper);
            assertNotEquals(Boolean.TRUE, result.isError());
        }

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() {
            final McpSchema.CallToolResult result =
                    badEngine.executeQuery(Map.of("sql", "SELECT 1"), mapper);
            assertEquals(Boolean.TRUE, result.isError());
        }

        @Test
        @DisplayName("executes query with bound parameter values")
        void executesQueryWithBoundParams() {
            final McpSchema.CallToolResult result =
                    engine.executeQuery(
                            Map.of(
                                    "sql",
                                    "SELECT user_id FROM users WHERE user_id = ?",
                                    "values",
                                    List.of(1)),
                            mapper);
            assertNotNull(result);
            // May or may not find rows depending on seeded data — just assert no error from JDBC
            assertFalse(
                    firstText(result).contains("SQL execution failed"),
                    "Parameterised SELECT should not fail at the JDBC level");
        }
    }

    // =========================================================================
    // listTables
    // =========================================================================

    @Nested
    @DisplayName("listTables")
    class ListTablesTests {

        @Test
        @DisplayName("returns a non-error result containing a tables list")
        void returnsTablesList() {
            final McpSchema.CallToolResult result = engine.listTables(mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("tables"));
        }

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() {
            final McpSchema.CallToolResult result = badEngine.listTables(mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("Failed to list tables"));
        }
    }

    // =========================================================================
    // getTableSchema
    // =========================================================================

    @Nested
    @DisplayName("getTableSchema")
    class GetTableSchemaTests {

        @Test
        @DisplayName("returns schema for an existing table")
        void returnsSchemaForExistingTable() {
            final McpSchema.CallToolResult result =
                    engine.getTableSchema(Map.of("tableName", "users"), mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("users"));
        }

        @Test
        @DisplayName("returns error when tableName is absent")
        void returnsErrorWhenTableNameAbsent() {
            final McpSchema.CallToolResult result = engine.getTableSchema(Map.of(), mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("'tableName' is required"));
        }

        @Test
        @DisplayName("returns error when tableName is blank")
        void returnsErrorWhenTableNameBlank() {
            final McpSchema.CallToolResult result =
                    engine.getTableSchema(Map.of("tableName", "   "), mapper);
            assertEquals(Boolean.TRUE, result.isError());
        }

        @Test
        @DisplayName("returns error when tableName is an invalid identifier")
        void returnsErrorWhenTableNameInvalid() {
            final McpSchema.CallToolResult result =
                    engine.getTableSchema(Map.of("tableName", "bad table; DROP"), mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("Invalid table name"));
        }

        @Test
        @DisplayName("returns error for unknown table")
        void returnsErrorForUnknownTable() {
            final McpSchema.CallToolResult result =
                    engine.getTableSchema(Map.of("tableName", "nonexistent_xyz"), mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("Table not found"));
        }

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() {
            final McpSchema.CallToolResult result =
                    badEngine.getTableSchema(Map.of("tableName", "users"), mapper);
            assertEquals(Boolean.TRUE, result.isError());
        }
    }

    // =========================================================================
    // getDatabaseInfo
    // =========================================================================

    @Nested
    @DisplayName("getDatabaseInfo")
    class GetDatabaseInfoTests {

        @Test
        @DisplayName("returns database metadata including tableCount")
        void returnsDatabaseMetadata() {
            final McpSchema.CallToolResult result = engine.getDatabaseInfo(mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("tableCount"));
        }

        @Test
        @DisplayName("returns approximateSizeBytes in the response")
        void returnsApproximateSizeBytes() {
            final McpSchema.CallToolResult result = engine.getDatabaseInfo(mapper);
            assertNotEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("approximateSizeBytes"));
        }

        @Test
        @DisplayName("returns error when database is not accessible")
        void returnsErrorForBadDbPath() {
            final McpSchema.CallToolResult result = badEngine.getDatabaseInfo(mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("Failed to get database info"));
        }
    }
}
