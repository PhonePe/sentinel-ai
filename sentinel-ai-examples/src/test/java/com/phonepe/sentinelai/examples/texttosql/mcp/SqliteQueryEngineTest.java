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
import io.modelcontextprotocol.spec.McpSchema.TextContent;

import org.junit.jupiter.api.BeforeAll;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SqliteQueryEngine}.
 *
 * <p>All handler methods are public, so no reflection is required.
 */
class SqliteQueryEngineTest {

    @TempDir
    static Path tempDir;

    static SqliteQueryEngine engine;
    static SqliteQueryEngine badEngine;
    static ObjectMapper mapper;

    @Nested
    class ExecuteQueryTests {

        @Test
        void executesQueryWithBoundParams() {
            final var result = engine.executeQuery(
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

        @Test
        void executesWithEmptyValuesList() {
            final var result = engine.executeQuery(
                                                   Map.of("sql",
                                                          "SELECT COUNT(*) FROM users",
                                                          "values",
                                                          List.of()),
                                                   mapper);
            assertNotEquals(Boolean.TRUE, result.isError());
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
        void rejectsInvalidStatements(String sql) {
            final var result = engine.executeQuery(Map.of("sql", sql), mapper);
            assertEquals(Boolean.TRUE, result.isError());
            if (sql.startsWith("INSERT")) {
                assertTrue(firstText(result).contains("INSERT"));
            }
        }

        @Test
        void returnsErrorForBadDbPath() {
            final var result = badEngine.executeQuery(Map.of("sql", "SELECT 1"), mapper);
            assertEquals(Boolean.TRUE, result.isError());
        }

        @Test
        void returnsErrorWhenSqlAbsent() {
            final var result = engine.executeQuery(Map.of(), mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("'sql' is required"));
        }

        @Test
        void returnsErrorWhenSqlBlank() {
            final var result = engine.executeQuery(Map.of("sql", "   "), mapper);
            assertEquals(Boolean.TRUE, result.isError());
        }

        @Test
        void returnsRowsForValidSelect() {
            final var result = engine.executeQuery(Map.of("sql", "SELECT 1 AS one"), mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError(), "Valid SELECT should succeed");
            assertTrue(firstText(result).contains("rows"));
        }

        @Test
        void returnsRowsFromUsersTable() {
            final var result = engine.executeQuery(Map.of("sql",
                                                          "SELECT user_id FROM users LIMIT 3"),
                                                   mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError(), "SELECT from users should succeed");
            assertTrue(firstText(result).contains("rows"));
        }
    }

    @Nested
    class GetDatabaseInfoTests {

        @Test
        void returnsApproximateSizeBytes() {
            final var result = engine.getDatabaseInfo(mapper);
            assertNotEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("approximateSizeBytes"));
        }

        @Test
        void returnsDatabaseMetadata() {
            final var result = engine.getDatabaseInfo(mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("tableCount"));
        }

        @Test
        void returnsErrorForBadDbPath() {
            final var result = badEngine.getDatabaseInfo(mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("Failed to get database info"));
        }
    }

    // =========================================================================
    // executeQuery
    // =========================================================================

    @Nested
    class GetTableSchemaTests {

        @Test
        void returnsErrorForBadDbPath() {
            final var result = badEngine.getTableSchema(Map.of("tableName", "users"), mapper);
            assertEquals(Boolean.TRUE, result.isError());
        }

        @Test
        void returnsErrorForUnknownTable() {
            final var result = engine.getTableSchema(Map.of("tableName", "nonexistent_xyz"),
                                                     mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("Table not found"));
        }

        @Test
        void returnsErrorWhenTableNameAbsent() {
            final var result = engine.getTableSchema(Map.of(), mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("'tableName' is required"));
        }

        @Test
        void returnsErrorWhenTableNameBlank() {
            final var result = engine.getTableSchema(Map.of("tableName", "   "), mapper);
            assertEquals(Boolean.TRUE, result.isError());
        }

        @Test
        void returnsErrorWhenTableNameInvalid() {
            final var result = engine.getTableSchema(Map.of("tableName", "bad table; DROP"),
                                                     mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("Invalid table name"));
        }

        @Test
        void returnsSchemaForExistingTable() {
            final var result = engine.getTableSchema(Map.of("tableName", "users"), mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("users"));
        }
    }

    // =========================================================================
    // listTables
    // =========================================================================

    @Nested
    class ListTablesTests {

        @Test
        void returnsErrorForBadDbPath() {
            final var result = badEngine.listTables(mapper);
            assertEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("Failed to list tables"));
        }

        @Test
        void returnsTablesList() {
            final var result = engine.listTables(mapper);
            assertNotNull(result);
            assertNotEquals(Boolean.TRUE, result.isError());
            assertTrue(firstText(result).contains("tables"));
        }
    }

    // =========================================================================
    // getTableSchema
    // =========================================================================

    @BeforeAll
    static void setUp() {
        final var dbPath = tempDir.resolve("query-engine-test.db");
        DatabaseInitializer.ensureInitialised(dbPath);

        engine = new SqliteQueryEngine(dbPath.toAbsolutePath().toString());
        badEngine = new SqliteQueryEngine("/nonexistent/path/to/db.sqlite");

        mapper = JsonUtils.createMapper();
    }

    // =========================================================================
    // getDatabaseInfo
    // =========================================================================

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
}
