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

package com.phonepe.sentinelai.examples.texttosql.server;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;

import jakarta.ws.rs.core.Response;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SqliteRestResource} exercising all eight endpoints by calling them directly
 * (no embedded HTTP server required).
 */
class SqliteRestResourceTest {

    @TempDir
    static Path tempDir;

    static SqliteRestResource resource;
    static ObjectMapper mapper;

    /**
     * Uses a deliberately broken (non-existent directory) DB path so that
     * {@code connect()} fails and each endpoint's {@code catch(Exception e)} branch is covered.
     */
    @Nested
    class BrokenDbTests {

        private final SqliteRestResource broken = new SqliteRestResource("/nonexistent/path/to/broken.db",
                                                                         new ObjectMapper());

        @Test
        void createRecordReturns500() {
            Map<String, Object> body = new HashMap<>();
            body.put("data", Map.of("seller_name", "test"));
            Response resp = broken.createRecord("sellers", body);
            assertEquals(500, resp.getStatus());
        }

        @Test
        void deleteRecordsReturns500() {
            Map<String, Object> body = new HashMap<>();
            body.put("conditions", Map.of("user_id", "1"));
            Response resp = broken.deleteRecords("users", body);
            assertEquals(500, resp.getStatus());
        }

        @Test
        void executeQueryReturns500() {
            Response resp = broken.executeQuery(Map.of("sql", "SELECT 1"));
            assertEquals(500, resp.getStatus());
        }

        @Test
        void getDatabaseInfoReturns500() {
            Response resp = broken.getDatabaseInfo();
            assertEquals(500, resp.getStatus());
        }

        @Test
        void getTableSchemaReturns500() {
            Response resp = broken.getTableSchema("users");
            assertEquals(500, resp.getStatus());
        }

        @Test
        void listTablesReturns500() {
            Response resp = broken.listTables();
            assertEquals(500, resp.getStatus());
        }

        @Test
        void readRecordsReturns500() {
            Response resp = broken.readRecords("users", null, null, null);
            assertEquals(500, resp.getStatus());
        }

        @Test
        void updateRecordsReturns500() {
            Map<String, Object> body = new HashMap<>();
            body.put("data", Map.of("city", "Mumbai"));
            body.put("conditions", Map.of("user_id", "1"));
            Response resp = broken.updateRecords("users", body);
            assertEquals(500, resp.getStatus());
        }
    }

    // =========================================================================
    // listTables
    // =========================================================================

    @Nested
    class CreateRecordHappyPathTests {

        @Test
        void insertSellerReturns200(@org.junit.jupiter.api.io.TempDir Path dir) {
            Path db = dir.resolve("crud.db");
            DatabaseInitializer.ensureInitialised(db);
            SqliteRestResource res = new SqliteRestResource(db.toAbsolutePath().toString(), mapper);

            Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("seller_name", "Test Seller");
            data.put("contact_email", "testseller_unique_" + System.nanoTime() + "@example.com");
            data.put("phone", "+910000000000");
            data.put("city", "Mumbai");
            data.put("country", "IN");
            data.put("rating", "4.5");
            data.put("total_reviews", "0");
            data.put("is_active", "1");
            data.put("joined_at", String.valueOf(System.currentTimeMillis() / 1000));

            Map<String, Object> body = new HashMap<>();
            body.put("data", data);

            Response resp = res.createRecord("sellers", body);
            assertEquals(200, resp.getStatus());
            String entity = (String) resp.getEntity();
            assertTrue(entity.contains("affectedRows"));
        }
    }

    // =========================================================================
    // getTableSchema
    // =========================================================================

    @Nested
    class CreateRecordTests {

        @Test
        void returns400WhenDataEmpty() {
            Map<String, Object> body = new HashMap<>();
            body.put("data", Map.of());
            Response resp = resource.createRecord("users", body);
            assertEquals(400, resp.getStatus());
        }

        @Test
        void returns400WhenDataMissing() {
            Response resp = resource.createRecord("users", Map.of());
            assertEquals(400, resp.getStatus());
        }
    }

    // =========================================================================
    // getDatabaseInfo
    // =========================================================================

    @Nested
    class DeleteRecordsHappyPathTests {

        @Test
        void deleteNonExistentRowReturns200(@org.junit.jupiter.api.io.TempDir Path dir) {
            Path db = dir.resolve("crud.db");
            DatabaseInitializer.ensureInitialised(db);
            SqliteRestResource res = new SqliteRestResource(db.toAbsolutePath().toString(), mapper);

            Map<String, Object> body = new HashMap<>();
            body.put("conditions", Map.of("user_id", "999999999"));

            Response resp = res.deleteRecords("users", body);
            assertEquals(200, resp.getStatus());
            String entity = (String) resp.getEntity();
            assertTrue(entity.contains("affectedRows"));
        }
    }

    // =========================================================================
    // executeQuery
    // =========================================================================

    @Nested
    class DeleteRecordsTests {

        @Test
        void returns400WhenConditionsEmpty() {
            Map<String, Object> body = new HashMap<>();
            body.put("conditions", Map.of());
            Response resp = resource.deleteRecords("users", body);
            assertEquals(400, resp.getStatus());
        }

        @Test
        void returns400WhenConditionsMissing() {
            Response resp = resource.deleteRecords("users", Map.of());
            assertEquals(400, resp.getStatus());
        }
    }

    // =========================================================================
    // readRecords
    // =========================================================================

    @Nested
    class ExecuteQueryTests {

        @Test
        void pragmaIsAllowed() {
            Map<String, Object> body = Map.of("sql", "PRAGMA table_info(users)");
            Response resp = resource.executeQuery(body);
            assertEquals(200, resp.getStatus());
        }

        @Test
        void returns400WhenSqlMissing() {
            Response resp = resource.executeQuery(Map.of());
            assertEquals(400, resp.getStatus());
        }

        @ParameterizedTest
        @CsvSource({
                "SELECT 1 AS one,200",
                "'   ',400"
        })
        void returnsExpectedStatusForSimpleQueryInputs(String sql, int expectedStatus) {
            Map<String, Object> body = Map.of("sql", sql);
            Response resp = resource.executeQuery(body);
            assertEquals(expectedStatus, resp.getStatus());
        }

        @Test
        void returnsRowsForUsersSelect() {
            Map<String, Object> body = Map.of("sql", "SELECT user_id FROM users LIMIT 3");
            Response resp = resource.executeQuery(body);
            assertEquals(200, resp.getStatus());
            String entity = (String) resp.getEntity();
            assertTrue(entity.contains("results"));
        }

        @ParameterizedTest
        @CsvSource({
                "INSERT INTO users (id) VALUES (999999)",
                "DELETE FROM users WHERE id = 999999",
                "UPDATE users SET id = 0 WHERE id = 999999",
                "DROP TABLE users"
        })
        void throwsForWriteStatements(String sql) {
            Map<String, Object> body = Map.of("sql", sql);
            assertThrows(
                         SqliteRestResource.WriteQueryNotAllowedException.class,
                         () -> resource.executeQuery(body));
        }

        @Test
        void withStatementIsAllowed() {
            Map<String, Object> body = Map.of("sql", "WITH cte AS (SELECT 1 AS x) SELECT * FROM cte");
            Response resp = resource.executeQuery(body);
            assertEquals(200, resp.getStatus());
        }
    }

    // =========================================================================
    // createRecord
    // =========================================================================

    @Nested
    class GetDatabaseInfoTests {

        @Test
        void returns200WithMetadata() {
            Response resp = resource.getDatabaseInfo();
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            assertTrue(body.contains("databasePath"));
            assertTrue(body.contains("tableCount"));
        }

        @Test
        void tableCountAtLeastFive() throws java.io.IOException {
            Response resp = resource.getDatabaseInfo();
            String body = (String) resp.getEntity();
            @SuppressWarnings("unchecked") Map<String, Object> parsed = mapper.readValue(body, Map.class);
            int count = (int) parsed.get("tableCount");
            assertTrue(count >= 5, "Should have at least 5 tables");
        }
    }

    // =========================================================================
    // updateRecords
    // =========================================================================

    @Nested
    class GetTableSchemaTests {

        @Test
        void returns200ForExistingTable() {
            Response resp = resource.getTableSchema("users");
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            assertTrue(body.contains("columns"), "Response should have columns");
            assertTrue(body.contains("users"));
        }

        @Test
        void returns404ForUnknownTable() {
            Response resp = resource.getTableSchema("nonexistent_table_xyz");
            assertEquals(404, resp.getStatus());
        }
    }

    // =========================================================================
    // deleteRecords
    // =========================================================================

    @Nested
    class IdentifierValidationTests {

        @Test
        void invalidIdentifierThrows() {
            final var exception = assertThrows(
                                               IllegalArgumentException.class,
                                               () -> resource.getTableSchema("bad table; DROP"));
            assertEquals("Invalid table name: bad table; DROP", exception.getMessage());
        }

        @Test
        void validIdentifierPassesValidation() {
            Response resp = resource.getTableSchema("valid_table_name");
            assertTrue(resp.getStatus() == 200 || resp.getStatus() == 404,
                       "Valid identifier should not throw an exception");
        }
    }

    // =========================================================================
    // createRecord — happy path (uses a temp DB to avoid corrupting shared one)
    // =========================================================================

    @Nested
    class ListTablesTests {

        @Test
        void containsExpectedTables() {
            Response resp = resource.listTables();
            String body = (String) resp.getEntity();
            assertTrue(body.contains("users"));
            assertTrue(body.contains("orders"));
            assertTrue(body.contains("catalog"));
            assertTrue(body.contains("sellers"));
            assertTrue(body.contains("inventory"));
        }

        @Test
        void returns200WithTablesList() {
            Response resp = resource.listTables();
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            assertTrue(body.contains("tables"), "Response should contain 'tables' key");
        }
    }

    // =========================================================================
    // updateRecords — happy path
    // =========================================================================

    @Nested
    class ReadRecordsTests {

        @Test
        void invalidTableIdentifierReturns500() {
            // Identifier with spaces — fails the SAFE_IDENTIFIER check and triggers exception
            assertThrows(Exception.class,
                         () -> resource.readRecords("bad table; DROP TABLE users; --", null, null, null));
        }

        @Test
        void limitAndOffsetRespected() throws java.io.IOException {
            Response resp = resource.readRecords("users", 3, 0, null);
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            @SuppressWarnings("unchecked") Map<String, Object> parsed = mapper.readValue(body, Map.class);
            int rowCount = (int) parsed.get("rowCount");
            assertTrue(rowCount <= 3, "Should return at most 3 rows");
        }

        @Test
        void returnsRowsForExistingTable() {
            Response resp = resource.readRecords("users", 5, 0, null);
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            assertTrue(body.contains("rows"));
        }

        @ParameterizedTest
        @CsvSource({
                "{},2,0",
                "{\"id\":\"99999999\"},,"
        })
        void supportsConditionsJson(String conditionsJson, Integer limit, Integer offset) {
            Response resp = resource.readRecords("users", limit, offset, conditionsJson);
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            assertTrue(body.contains("rows"));
        }
    }

    // =========================================================================
    // deleteRecords — happy path
    // =========================================================================

    @Nested
    class UpdateRecordsHappyPathTests {

        @Test
        void updateNonExistentRowReturns200(@org.junit.jupiter.api.io.TempDir Path dir) {
            Path db = dir.resolve("crud.db");
            DatabaseInitializer.ensureInitialised(db);
            SqliteRestResource res = new SqliteRestResource(db.toAbsolutePath().toString(), mapper);

            Map<String, Object> body = new HashMap<>();
            body.put("data", Map.of("city", "Delhi"));
            body.put("conditions", Map.of("user_id", "999999999"));

            Response resp = res.updateRecords("users", body);
            assertEquals(200, resp.getStatus());
            String entity = (String) resp.getEntity();
            assertTrue(entity.contains("affectedRows"));
        }
    }

    // =========================================================================
    // WriteQueryNotAllowedException
    // =========================================================================

    @Nested
    class UpdateRecordsTests {

        @Test
        void returns400WhenConditionsMissing() {
            Map<String, Object> data = Map.of("username", "newname");
            Map<String, Object> body = new HashMap<>();
            body.put("data", data);
            Response resp = resource.updateRecords("users", body);
            assertEquals(400, resp.getStatus());
        }

        @Test
        void returns400WhenDataMissing() {
            Map<String, Object> body = Map.of("conditions", Map.of("id", "1"));
            Response resp = resource.updateRecords("users", body);
            assertEquals(400, resp.getStatus());
        }
    }

    // =========================================================================
    // validateIdentifier (via public endpoints — indirect coverage)
    // =========================================================================

    @Nested
    class WriteQueryNotAllowedExceptionTests {

        @Test
        void exceptionMessagePreserved() {
            SqliteRestResource.WriteQueryNotAllowedException ex = new SqliteRestResource.WriteQueryNotAllowedException("test message");
            assertEquals("test message", ex.getMessage());
        }

        @Test
        void isRuntimeException() {
            SqliteRestResource.WriteQueryNotAllowedException ex = new SqliteRestResource.WriteQueryNotAllowedException("test");
            assertInstanceOf(RuntimeException.class, ex);
        }
    }

    // =========================================================================
    // Error paths — broken DB to trigger catch blocks
    // =========================================================================

    @BeforeAll
    static void setUp() {
        Path dbPath = tempDir.resolve("test.db");
        DatabaseInitializer.ensureInitialised(dbPath);
        mapper = new ObjectMapper();
        resource = new SqliteRestResource(dbPath.toAbsolutePath().toString(), mapper);
    }
}
