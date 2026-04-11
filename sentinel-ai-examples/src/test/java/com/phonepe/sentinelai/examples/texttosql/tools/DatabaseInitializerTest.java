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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("DatabaseInitializer")
class DatabaseInitializerTest {

    // =========================================================================
    // parseCsvLine — unit tests (package-private method)
    // =========================================================================

    @Nested
    @DisplayName("parseCsvLine")
    class ParseCsvLineTests {

        @Test
        @DisplayName("parses simple comma-separated values")
        void simpleValues() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("a,b,c");
            assertEquals(List.of("a", "b", "c"), tokens);
        }

        @Test
        @DisplayName("parses quoted field containing a comma")
        void quotedFieldWithComma() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("\"hello, world\",foo");
            assertEquals(List.of("hello, world", "foo"), tokens);
        }

        @Test
        @DisplayName("unescapes doubled quotes inside a quoted field")
        void doubledQuoteInsideQuotedField() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("\"say \"\"hi\"\"\",end");
            assertEquals(List.of("say \"hi\"", "end"), tokens);
        }

        @Test
        @DisplayName("parses single field with no commas")
        void singleField() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("only");
            assertEquals(List.of("only"), tokens);
        }

        @Test
        @DisplayName("empty string produces single empty token")
        void emptyStringProducesSingleToken() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("");
            assertEquals(List.of(""), tokens);
        }

        @Test
        @DisplayName("trailing comma produces empty last token")
        void trailingComma() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("a,b,");
            assertEquals(List.of("a", "b", ""), tokens);
        }

        @Test
        @DisplayName("leading comma produces empty first token")
        void leadingComma() {
            List<String> tokens = DatabaseInitializer.parseCsvLine(",b,c");
            assertEquals(List.of("", "b", "c"), tokens);
        }

        @Test
        @DisplayName("fully quoted field")
        void fullyQuotedField() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("\"hello\",\"world\"");
            assertEquals(List.of("hello", "world"), tokens);
        }

        @Test
        @DisplayName("quoted field with newline-like content")
        void quotedFieldMixed() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("\"value with spaces\",plain");
            assertEquals(List.of("value with spaces", "plain"), tokens);
        }

        @Test
        @DisplayName("parses header line of CSV")
        void parsesHeaderLine() {
            List<String> tokens =
                    DatabaseInitializer.parseCsvLine("id,user_id,seller_id,total_price,status");
            assertEquals(List.of("id", "user_id", "seller_id", "total_price", "status"), tokens);
        }
    }

    // =========================================================================
    // ensureInitialised — integration tests
    // =========================================================================

    @Nested
    @DisplayName("ensureInitialised")
    class EnsureInitialisedTests {

        @Test
        @DisplayName("creates database file when it does not exist")
        void createsDatabaseFile(@TempDir Path tempDir) {
            Path dbPath = tempDir.resolve("test.db");
            assertFalse(Files.exists(dbPath), "DB should not exist before init");

            DatabaseInitializer.ensureInitialised(dbPath);

            assertTrue(Files.exists(dbPath), "DB file should exist after init");
        }

        @Test
        @DisplayName("database has expected tables after init")
        void databaseHasExpectedTables(@TempDir Path tempDir) throws Exception {
            Path dbPath = tempDir.resolve("test.db");
            DatabaseInitializer.ensureInitialised(dbPath);

            try (Connection conn =
                         DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                    var stmt = conn.createStatement();
                    ResultSet rs =
                            stmt.executeQuery(
                                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name")) {
                java.util.List<String> tables = new java.util.ArrayList<>();
                while (rs.next()) {
                    tables.add(rs.getString("name"));
                }
                assertTrue(tables.contains("users"), "users table should exist");
                assertTrue(tables.contains("orders"), "orders table should exist");
                assertTrue(tables.contains("catalog"), "catalog table should exist");
                assertTrue(tables.contains("sellers"), "sellers table should exist");
                assertTrue(tables.contains("inventory"), "inventory table should exist");
            }
        }

        @Test
        @DisplayName("tables have rows after init")
        void tablesHaveRowsAfterInit(@TempDir Path tempDir) throws Exception {
            Path dbPath = tempDir.resolve("test.db");
            DatabaseInitializer.ensureInitialised(dbPath);

            try (Connection conn =
                            DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                    var stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM users")) {
                assertTrue(rs.next());
                assertTrue(rs.getLong("cnt") > 0, "users table should have seed data");
            }
        }

        @Test
        @DisplayName("calling ensureInitialised twice is idempotent")
        void idempotentOnSecondCall(@TempDir Path tempDir) throws Exception {
            Path dbPath = tempDir.resolve("test.db");
            DatabaseInitializer.ensureInitialised(dbPath);
            long sizeAfterFirst = Files.size(dbPath);

            // Second call should not re-create or corrupt the database
            DatabaseInitializer.ensureInitialised(dbPath);
            long sizeAfterSecond = Files.size(dbPath);

            // File should still exist and have same (or larger, due to WAL) size
            assertTrue(Files.exists(dbPath));
            assertTrue(sizeAfterSecond >= sizeAfterFirst - 1024,
                    "Database should not shrink after second init call");
        }

        @Test
        @DisplayName("creates parent directories when they do not exist")
        void createsParentDirectories(@TempDir Path tempDir) throws IOException {
            Path dbPath = tempDir.resolve("nested").resolve("deep").resolve("test.db");
            assertFalse(Files.exists(dbPath.getParent()));

            DatabaseInitializer.ensureInitialised(dbPath);

            assertTrue(Files.exists(dbPath), "DB file should exist even with nested parent dirs");
        }
    }
}
