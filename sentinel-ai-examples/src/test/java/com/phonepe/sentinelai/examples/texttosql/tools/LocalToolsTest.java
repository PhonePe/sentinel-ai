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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;
import com.phonepe.sentinelai.examples.texttosql.tools.model.TableDescRequest;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalToolsTest {

    @TempDir
    static Path tempDir;

    static LocalTools localTools;
    static Path dbPath;

    @Nested
    class AppendTableDdlTests {

        @Test
        void appendsDdlForExistingTable() throws Exception {
            Method method = LocalTools.class.getDeclaredMethod(
                                                               "appendTableDdl",
                                                               Connection.class,
                                                               String.class,
                                                               StringBuilder.class);
            method.setAccessible(true);

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
                StringBuilder sb = new StringBuilder();
                method.invoke(localTools, conn, "users", sb);
                String result = sb.toString();
                assertNotNull(result);
                assertFalse(result.isBlank(), "appendTableDdl should produce non-empty output");
                assertTrue(result.contains("Columns"), "Output should contain Columns section");
            }
        }

        @Test
        void appendsDdlForOrdersTable() throws Exception {
            Method method = LocalTools.class.getDeclaredMethod(
                                                               "appendTableDdl",
                                                               Connection.class,
                                                               String.class,
                                                               StringBuilder.class);
            method.setAccessible(true);

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
                StringBuilder sb = new StringBuilder();
                method.invoke(localTools, conn, "orders", sb);
                String result = sb.toString();
                assertFalse(result.isBlank(), "orders table DDL should be non-empty");
            }
        }

        @Test
        void handlesNonExistentTable() throws Exception {
            Method method = LocalTools.class.getDeclaredMethod(
                                                               "appendTableDdl",
                                                               Connection.class,
                                                               String.class,
                                                               StringBuilder.class);
            method.setAccessible(true);

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
                StringBuilder sb = new StringBuilder();
                // Should not throw for a non-existent table; just produces partial output
                assertDoesNotThrow(() -> method.invoke(localTools, conn, "nonexistent_xyz", sb));
            }
        }
    }

    // =========================================================================
    // name()
    // =========================================================================

    @Nested
    class ConvertEpochTests {

        @Test
        void epochZeroInUtc() {
            String result = localTools.convertEpochToLocalDateTime(0L, "UTC");
            assertEquals("1970/01/01 00:00:00", result);
        }

        @Test
        void invalidTimezoneReturnsError() {
            String result = localTools.convertEpochToLocalDateTime(0L, "Invalid/Zone");
            assertTrue(result.startsWith("Invalid epoch or timezone"),
                       "Should return error message for invalid timezone");
        }

        @Test
        void knownEpochInKolkata() {
            // 2024-01-01 00:00:00 UTC = 2024-01-01 05:30:00 IST
            long epoch = 1704067200L;
            String result = localTools.convertEpochToLocalDateTime(epoch, "Asia/Kolkata");
            assertEquals("2024/01/01 05:30:00", result);
        }
    }

    // =========================================================================
    // convertEpochToLocalDateTime
    // =========================================================================

    @Nested
    class FormatResultsAsTableTests {

        @Test
        void emptyResultsReturnsNoResults() {
            SqlQueryResult result = new SqlQueryResult("SELECT 1", List.of(), null, 0L);
            String table = LocalTools.formatResultsAsTable(result);
            assertEquals("No results found.", table);
        }

        @Test
        void malformedJsonReturnsError() {
            SqlQueryResult result = new SqlQueryResult(
                                                       "SELECT 1",
                                                       List.of("not-valid-json{{{{"),
                                                       null,
                                                       0L);
            String table = LocalTools.formatResultsAsTable(result);
            assertTrue(
                       table.startsWith("Could not format results"),
                       "Malformed JSON should produce an error message");
        }

        @Test
        void nullColumnValueRenderedAsNull() {
            SqlQueryResult result = new SqlQueryResult(
                                                       "SELECT id, name FROM users",
                                                       List.of("{\"id\":1,\"name\":null}"),
                                                       null,
                                                       0L);
            String table = LocalTools.formatResultsAsTable(result);
            assertTrue(table.contains("NULL"), "null value should be rendered as NULL");
        }

        @Test
        void nullResultsReturnsNoResults() {
            SqlQueryResult result = new SqlQueryResult("SELECT 1", null, null, 0L);
            String table = LocalTools.formatResultsAsTable(result);
            assertEquals("No results found.", table);
        }

        @Test
        void rendersAsciiTableForValidRow() {
            SqlQueryResult result = new SqlQueryResult(
                                                       "SELECT id, name FROM users",
                                                       List.of("{\"id\":1,\"name\":\"Alice\"}"),
                                                       null,
                                                       0L);
            String table = LocalTools.formatResultsAsTable(result);
            assertNotNull(table);
            assertFalse(table.isBlank());
            assertTrue(table.contains("id"), "Table should contain column header 'id'");
            assertTrue(table.contains("name"), "Table should contain column header 'name'");
            assertTrue(table.contains("Alice"), "Table should contain the row value");
        }

        @Test
        void rendersMultipleRows() {
            SqlQueryResult result = new SqlQueryResult(
                                                       "SELECT id FROM users",
                                                       List.of("{\"id\":1}", "{\"id\":2}", "{\"id\":3}"),
                                                       null,
                                                       0L);
            String table = LocalTools.formatResultsAsTable(result);
            assertNotNull(table);
            // All three ids should appear
            assertTrue(table.contains("1"));
            assertTrue(table.contains("2"));
            assertTrue(table.contains("3"));
        }
    }

    // =========================================================================
    // getCurrentDateTime
    // =========================================================================

    @Nested
    class GetColumnDescTests {

        @Test
        void returnsDescriptionForExistingColumn() {
            String result = localTools.getColumnDescription("users", "email");
            assertNotNull(result);
            assertFalse(result.isBlank());
            // Should not start with error messages
            assertFalse(result.startsWith("Table not found"),
                        "Should return a real description for users.email");
            assertFalse(result.startsWith("Column not found"),
                        "Should return a real description for users.email");
        }

        @Test
        void returnsErrorForUnknownColumn() {
            String result = localTools.getColumnDescription("users", "nonexistent_column");
            assertTrue(result.startsWith("Column not found"));
        }

        @Test
        void returnsErrorForUnknownTable() {
            String result = localTools.getColumnDescription("nonexistent", "col");
            assertTrue(result.startsWith("Table not found"));
        }
    }

    // =========================================================================
    // searchSchema
    // =========================================================================

    @Nested
    class GetCurrentDateTimeTests {

        @Test
        void invalidTimezoneReturnsError() {
            String result = localTools.getCurrentDateTime("Not/AZone");
            assertTrue(result.startsWith("Invalid timezone"),
                       "Should return error for invalid timezone");
        }

        @Test
        void resultContainsEpochSeconds() {
            String result = localTools.getCurrentDateTime("UTC");
            assertTrue(result.contains("epoch seconds"),
                       "Result should include epoch seconds");
        }

        @Test
        void resultContainsTimezone() {
            String result = localTools.getCurrentDateTime("Asia/Kolkata");
            assertTrue(result.contains("Asia/Kolkata"),
                       "Result should mention the requested timezone");
        }

        @Test
        void returnsNonBlankForValidTimezone() {
            String result = localTools.getCurrentDateTime("UTC");
            assertNotNull(result);
            assertFalse(result.isBlank());
        }
    }

    // =========================================================================
    // getTableDescription
    // =========================================================================

    @Nested
    class GetTableDescTests {

        @Test
        void multipleTableNames() {
            TableDescRequest req = new TableDescRequest(List.of("users", "orders"));
            String result = localTools.getTableDescription(req);
            assertTrue(result.contains("users"));
            assertTrue(result.contains("orders"));
        }

        @Test
        void nullTableNamesReturnsMessage() {
            TableDescRequest req = new TableDescRequest(null);
            String result = localTools.getTableDescription(req);
            assertNotNull(result);
            assertFalse(result.isBlank());
        }

        @Test
        void returnsDescriptionForExistingTable() {
            TableDescRequest req = new TableDescRequest(List.of("users"));
            String result = localTools.getTableDescription(req);
            assertNotNull(result);
            assertTrue(result.contains("users"), "Result should mention the users table");
        }

        @Test
        void returnsNotFoundForUnknownTable() {
            TableDescRequest req = new TableDescRequest(List.of("nonexistent_table"));
            String result = localTools.getTableDescription(req);
            assertTrue(result.contains("Not found"),
                       "Should say 'Not found' for unknown table");
        }
    }

    // =========================================================================
    // getColumnDescription
    // =========================================================================

    @Nested
    class GetTableRowCountsTests {

        @Test
        void containsAllTables() {
            String result = localTools.getTableRowCounts();
            assertTrue(result.contains("users"));
            assertTrue(result.contains("orders"));
            assertTrue(result.contains("catalog"));
            assertTrue(result.contains("sellers"));
            assertTrue(result.contains("inventory"));
        }

        @Test
        void returnsRowCountHeader() {
            String result = localTools.getTableRowCounts();
            assertNotNull(result);
            assertTrue(result.contains("Row counts per table"),
                       "Result should contain row counts header");
        }
    }

    // =========================================================================
    // getTableRowCounts
    // =========================================================================

    @Nested
    class NameTests {

        @Test
        void nameReturnsExpected() {
            assertEquals("local_sql_tools", localTools.name());
        }
    }

    // =========================================================================
    // formatResultsAsTable (static)
    // =========================================================================

    @Nested
    class SearchSchemaTests {

        @Test
        void noMatchesReturnsMessage() {
            // Extremely unusual query unlikely to match anything
            String result = localTools.searchSchema("xyzzy_nonexistent_term_12345", 5);
            assertNotNull(result);
            // Either "No schema matches found" or actual (few) results via KNN
            assertFalse(result.isBlank());
        }

        @Test
        void nonPositiveTopKUsesDefault() {
            String result = localTools.searchSchema("catalog product", 0);
            assertNotNull(result);
            assertFalse(result.isBlank());
        }

        @Test
        void resultContainsEntries() {
            String result = localTools.searchSchema("orders delivery status", 8);
            assertTrue(result.contains("TABLE") || result.contains("COLUMN"),
                       "Result should contain TABLE or COLUMN entries");
        }

        @Test
        void returnsResultsForRelevantQuery() {
            String result = localTools.searchSchema("user email address", 5);
            assertNotNull(result);
            assertFalse(result.isBlank());
            assertTrue(result.contains("Schema search results"),
                       "Result should contain schema search header");
        }
    }

    @BeforeAll
    static void setUp() throws IOException {
        dbPath = tempDir.resolve("test.db");
        // Seed the database so LocalTools can connect to it
        DatabaseInitializer.ensureInitialised(dbPath);
        localTools = new LocalTools(dbPath.toAbsolutePath().toString(), tempDir);
    }
}
