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

import static org.junit.jupiter.api.Assertions.*;

import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;
import com.phonepe.sentinelai.examples.texttosql.tools.model.TableDescRequest;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("LocalTools")
class LocalToolsTest {

    @TempDir
    static Path tempDir;

    static LocalTools localTools;
    static Path dbPath;

    @BeforeAll
    static void setUp() throws Exception {
        dbPath = tempDir.resolve("test.db");
        // Seed the database so LocalTools can connect to it
        DatabaseInitializer.ensureInitialised(dbPath);
        localTools = new LocalTools(dbPath.toAbsolutePath().toString(), tempDir);
    }

    // =========================================================================
    // name()
    // =========================================================================

    @Test
    @DisplayName("name() returns expected toolbox name")
    void nameReturnsExpected() {
        assertEquals("local_sql_tools", localTools.name());
    }

    // =========================================================================
    // convertEpochToLocalDateTime
    // =========================================================================

    @Nested
    @DisplayName("convertEpochToLocalDateTime")
    class ConvertEpochTests {

        @Test
        @DisplayName("converts epoch 0 to 1970-01-01 in UTC")
        void epochZeroInUtc() {
            String result = localTools.convertEpochToLocalDateTime(0L, "UTC");
            assertEquals("1970/01/01 00:00:00", result);
        }

        @Test
        @DisplayName("converts a known epoch in Asia/Kolkata")
        void knownEpochInKolkata() {
            // 2024-01-01 00:00:00 UTC = 2024-01-01 05:30:00 IST
            long epoch = 1704067200L;
            String result = localTools.convertEpochToLocalDateTime(epoch, "Asia/Kolkata");
            assertEquals("2024/01/01 05:30:00", result);
        }

        @Test
        @DisplayName("returns error string for invalid timezone")
        void invalidTimezoneReturnsError() {
            String result = localTools.convertEpochToLocalDateTime(0L, "Invalid/Zone");
            assertTrue(result.startsWith("Invalid epoch or timezone"),
                    "Should return error message for invalid timezone");
        }
    }

    // =========================================================================
    // getCurrentDateTime
    // =========================================================================

    @Nested
    @DisplayName("getCurrentDateTime")
    class GetCurrentDateTimeTests {

        @Test
        @DisplayName("returns non-null, non-blank string for valid timezone")
        void returnsNonBlankForValidTimezone() {
            String result = localTools.getCurrentDateTime("UTC");
            assertNotNull(result);
            assertFalse(result.isBlank());
        }

        @Test
        @DisplayName("result contains timezone name")
        void resultContainsTimezone() {
            String result = localTools.getCurrentDateTime("Asia/Kolkata");
            assertTrue(result.contains("Asia/Kolkata"),
                    "Result should mention the requested timezone");
        }

        @Test
        @DisplayName("result contains epoch seconds")
        void resultContainsEpochSeconds() {
            String result = localTools.getCurrentDateTime("UTC");
            assertTrue(result.contains("epoch seconds"),
                    "Result should include epoch seconds");
        }

        @Test
        @DisplayName("returns error string for invalid timezone")
        void invalidTimezoneReturnsError() {
            String result = localTools.getCurrentDateTime("Not/AZone");
            assertTrue(result.startsWith("Invalid timezone"),
                    "Should return error for invalid timezone");
        }
    }

    // =========================================================================
    // searchSchema
    // =========================================================================

    @Nested
    @DisplayName("searchSchema")
    class SearchSchemaTests {

        @Test
        @DisplayName("returns schema results for a relevant query")
        void returnsResultsForRelevantQuery() {
            String result = localTools.searchSchema("user email address", 5);
            assertNotNull(result);
            assertFalse(result.isBlank());
            assertTrue(result.contains("Schema search results"),
                    "Result should contain schema search header");
        }

        @Test
        @DisplayName("result contains table and column entries")
        void resultContainsEntries() {
            String result = localTools.searchSchema("orders delivery status", 8);
            assertTrue(result.contains("TABLE") || result.contains("COLUMN"),
                    "Result should contain TABLE or COLUMN entries");
        }

        @Test
        @DisplayName("topK <= 0 is treated as default (8)")
        void nonPositiveTopKUsesDefault() {
            String result = localTools.searchSchema("catalog product", 0);
            assertNotNull(result);
            assertFalse(result.isBlank());
        }

        @Test
        @DisplayName("query with no matches returns informative message")
        void noMatchesReturnsMessage() {
            // Extremely unusual query unlikely to match anything
            String result = localTools.searchSchema("xyzzy_nonexistent_term_12345", 5);
            assertNotNull(result);
            // Either "No schema matches found" or actual (few) results via KNN
            assertFalse(result.isBlank());
        }
    }

    // =========================================================================
    // getTableDescription
    // =========================================================================

    @Nested
    @DisplayName("getTableDescription")
    class GetTableDescTests {

        @Test
        @DisplayName("returns description for existing table")
        void returnsDescriptionForExistingTable() {
            TableDescRequest req = new TableDescRequest(List.of("users"));
            String result = localTools.getTableDescription(req);
            assertNotNull(result);
            assertTrue(result.contains("users"), "Result should mention the users table");
        }

        @Test
        @DisplayName("returns 'Not found' for unknown table")
        void returnsNotFoundForUnknownTable() {
            TableDescRequest req = new TableDescRequest(List.of("nonexistent_table"));
            String result = localTools.getTableDescription(req);
            assertTrue(result.contains("Not found"),
                    "Should say 'Not found' for unknown table");
        }

        @Test
        @DisplayName("returns message when tableNames is null")
        void nullTableNamesReturnsMessage() {
            TableDescRequest req = new TableDescRequest(null);
            String result = localTools.getTableDescription(req);
            assertNotNull(result);
            assertFalse(result.isBlank());
        }

        @Test
        @DisplayName("handles multiple table names")
        void multipleTableNames() {
            TableDescRequest req = new TableDescRequest(List.of("users", "orders"));
            String result = localTools.getTableDescription(req);
            assertTrue(result.contains("users"));
            assertTrue(result.contains("orders"));
        }
    }

    // =========================================================================
    // getColumnDescription
    // =========================================================================

    @Nested
    @DisplayName("getColumnDescription")
    class GetColumnDescTests {

        @Test
        @DisplayName("returns description for existing column")
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
        @DisplayName("returns error for unknown table")
        void returnsErrorForUnknownTable() {
            String result = localTools.getColumnDescription("nonexistent", "col");
            assertTrue(result.startsWith("Table not found"));
        }

        @Test
        @DisplayName("returns error for unknown column in existing table")
        void returnsErrorForUnknownColumn() {
            String result = localTools.getColumnDescription("users", "nonexistent_column");
            assertTrue(result.startsWith("Column not found"));
        }
    }

    // =========================================================================
    // getTableRowCounts
    // =========================================================================

    @Nested
    @DisplayName("getTableRowCounts")
    class GetTableRowCountsTests {

        @Test
        @DisplayName("returns non-blank result with row count header")
        void returnsRowCountHeader() {
            String result = localTools.getTableRowCounts();
            assertNotNull(result);
            assertTrue(result.contains("Row counts per table"),
                    "Result should contain row counts header");
        }

        @Test
        @DisplayName("result contains all expected tables")
        void containsAllTables() {
            String result = localTools.getTableRowCounts();
            assertTrue(result.contains("users"));
            assertTrue(result.contains("orders"));
            assertTrue(result.contains("catalog"));
            assertTrue(result.contains("sellers"));
            assertTrue(result.contains("inventory"));
        }
    }

    // =========================================================================
    // formatResultsAsTable (static)
    // =========================================================================

    @Nested
    @DisplayName("formatResultsAsTable")
    class FormatResultsAsTableTests {

        @Test
        @DisplayName("returns 'No results found' for null results")
        void nullResultsReturnsNoResults() {
            SqlQueryResult result = new SqlQueryResult("SELECT 1", null, null, 0L);
            String table = LocalTools.formatResultsAsTable(result);
            assertEquals("No results found.", table);
        }

        @Test
        @DisplayName("returns 'No results found' for empty results")
        void emptyResultsReturnsNoResults() {
            SqlQueryResult result = new SqlQueryResult("SELECT 1", List.of(), null, 0L);
            String table = LocalTools.formatResultsAsTable(result);
            assertEquals("No results found.", table);
        }

        @Test
        @DisplayName("renders ASCII table for a valid JSON row")
        void rendersAsciiTableForValidRow() {
            SqlQueryResult result =
                    new SqlQueryResult(
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
        @DisplayName("handles null column value gracefully (renders as NULL)")
        void nullColumnValueRenderedAsNull() {
            SqlQueryResult result =
                    new SqlQueryResult(
                            "SELECT id, name FROM users",
                            List.of("{\"id\":1,\"name\":null}"),
                            null,
                            0L);
            String table = LocalTools.formatResultsAsTable(result);
            assertTrue(table.contains("NULL"), "null value should be rendered as NULL");
        }

        @Test
        @DisplayName("renders multiple rows correctly")
        void rendersMultipleRows() {
            SqlQueryResult result =
                    new SqlQueryResult(
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

        @Test
        @DisplayName("returns error message for malformed JSON row")
        void malformedJsonReturnsError() {
            SqlQueryResult result =
                    new SqlQueryResult(
                            "SELECT 1",
                            List.of("not-valid-json{{{{"),
                            null,
                            0L);
            String table = LocalTools.formatResultsAsTable(result);
            assertTrue(
                    table.startsWith("Could not format results"),
                    "Malformed JSON should produce an error message");
        }
    }

    // =========================================================================
    // appendTableDdl (private method — covered via reflection)
    // =========================================================================

    @Nested
    @DisplayName("appendTableDdl")
    class AppendTableDdlTests {

        @Test
        @DisplayName("appends DDL and column info for an existing table")
        void appendsDdlForExistingTable() throws Exception {
            Method method =
                    LocalTools.class.getDeclaredMethod(
                            "appendTableDdl", Connection.class, String.class, StringBuilder.class);
            method.setAccessible(true);

            try (Connection conn =
                    DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
                StringBuilder sb = new StringBuilder();
                method.invoke(localTools, conn, "users", sb);
                String result = sb.toString();
                assertNotNull(result);
                assertFalse(result.isBlank(), "appendTableDdl should produce non-empty output");
                assertTrue(result.contains("Columns"), "Output should contain Columns section");
            }
        }

        @Test
        @DisplayName("appends column info for orders table")
        void appendsDdlForOrdersTable() throws Exception {
            Method method =
                    LocalTools.class.getDeclaredMethod(
                            "appendTableDdl", Connection.class, String.class, StringBuilder.class);
            method.setAccessible(true);

            try (Connection conn =
                    DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
                StringBuilder sb = new StringBuilder();
                method.invoke(localTools, conn, "orders", sb);
                String result = sb.toString();
                assertFalse(result.isBlank(), "orders table DDL should be non-empty");
            }
        }

        @Test
        @DisplayName("handles non-existent table gracefully")
        void handlesNonExistentTable() throws Exception {
            Method method =
                    LocalTools.class.getDeclaredMethod(
                            "appendTableDdl", Connection.class, String.class, StringBuilder.class);
            method.setAccessible(true);

            try (Connection conn =
                    DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath())) {
                StringBuilder sb = new StringBuilder();
                // Should not throw for a non-existent table; just produces partial output
                assertDoesNotThrow(() -> method.invoke(localTools, conn, "nonexistent_xyz", sb));
            }
        }
    }
}
