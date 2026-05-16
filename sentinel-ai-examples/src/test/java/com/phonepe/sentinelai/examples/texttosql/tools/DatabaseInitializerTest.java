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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseInitializerTest {

    // =========================================================================
    // parseCsvLine — unit tests (package-private method)
    // =========================================================================

    @Nested
    class EnsureInitialisedTests {

        @Test
        void createsDatabaseFile(@TempDir Path tempDir) {
            Path dbPath = tempDir.resolve("test.db");
            assertFalse(Files.exists(dbPath), "DB should not exist before init");

            DatabaseInitializer.ensureInitialised(dbPath);

            assertTrue(Files.exists(dbPath), "DB file should exist after init");
        }

        @Test
        void createsParentDirectories(@TempDir Path tempDir) {
            Path dbPath = tempDir.resolve("nested").resolve("deep").resolve("test.db");
            assertFalse(Files.exists(dbPath.getParent()));

            DatabaseInitializer.ensureInitialised(dbPath);

            assertTrue(Files.exists(dbPath), "DB file should exist even with nested parent dirs");
        }

        @Test
        void databaseHasExpectedTables(@TempDir Path tempDir) throws Exception {
            Path dbPath = tempDir.resolve("test.db");
            DatabaseInitializer.ensureInitialised(dbPath);

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                 var stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
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
        void tablesHaveRowsAfterInit(@TempDir Path tempDir) throws Exception {
            Path dbPath = tempDir.resolve("test.db");
            DatabaseInitializer.ensureInitialised(dbPath);

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
                 var stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM users")) {
                assertTrue(rs.next());
                assertTrue(rs.getLong("cnt") > 0, "users table should have seed data");
            }
        }
    }

    // =========================================================================
    // ensureInitialised — integration tests
    // =========================================================================

    @Nested
    class ParseCsvLineTests {

        @Test
        void doubledQuoteInsideQuotedField() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("\"say \"\"hi\"\"\",end");
            assertEquals(List.of("say \"hi\"", "end"), tokens);
        }

        @Test
        void emptyStringProducesSingleToken() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("");
            assertEquals(List.of(""), tokens);
        }

        @Test
        void fullyQuotedField() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("\"hello\",\"world\"");
            assertEquals(List.of("hello", "world"), tokens);
        }

        @Test
        void leadingComma() {
            List<String> tokens = DatabaseInitializer.parseCsvLine(",b,c");
            assertEquals(List.of("", "b", "c"), tokens);
        }

        @Test
        void parsesHeaderLine() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("id,user_id,seller_id,total_price,status");
            assertEquals(List.of("id", "user_id", "seller_id", "total_price", "status"), tokens);
        }

        @Test
        void quotedFieldMixed() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("\"value with spaces\",plain");
            assertEquals(List.of("value with spaces", "plain"), tokens);
        }

        @Test
        void quotedFieldWithComma() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("\"hello, world\",foo");
            assertEquals(List.of("hello, world", "foo"), tokens);
        }

        @Test
        void simpleValues() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("a,b,c");
            assertEquals(List.of("a", "b", "c"), tokens);
        }

        @Test
        void singleField() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("only");
            assertEquals(List.of("only"), tokens);
        }

        @Test
        void trailingComma() {
            List<String> tokens = DatabaseInitializer.parseCsvLine("a,b,");
            assertEquals(List.of("a", "b", ""), tokens);
        }
    }
}
