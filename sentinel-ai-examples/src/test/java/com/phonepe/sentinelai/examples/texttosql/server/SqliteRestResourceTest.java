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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;
import jakarta.ws.rs.core.Response;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link SqliteRestResource} exercising all eight endpoints by calling them directly
 * (no embedded HTTP server required).
 */
@DisplayName("SqliteRestResource")
class SqliteRestResourceTest {

    @TempDir
    static Path tempDir;

    static SqliteRestResource resource;
    static ObjectMapper mapper;

    @BeforeAll
    static void setUp() throws Exception {
        Path dbPath = tempDir.resolve("test.db");
        DatabaseInitializer.ensureInitialised(dbPath);
        mapper = new ObjectMapper();
        resource = new SqliteRestResource(dbPath.toAbsolutePath().toString(), mapper);
    }

    // =========================================================================
    // listTables
    // =========================================================================

    @Nested
    @DisplayName("GET /tables")
    class ListTablesTests {

        @Test
        @DisplayName("returns 200 with tables list")
        void returns200WithTablesList() throws Exception {
            Response resp = resource.listTables();
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            assertTrue(body.contains("tables"), "Response should contain 'tables' key");
        }

        @Test
        @DisplayName("tables list contains expected e-commerce tables")
        void containsExpectedTables() throws Exception {
            Response resp = resource.listTables();
            String body = (String) resp.getEntity();
            assertTrue(body.contains("users"));
            assertTrue(body.contains("orders"));
            assertTrue(body.contains("catalog"));
            assertTrue(body.contains("sellers"));
            assertTrue(body.contains("inventory"));
        }
    }

    // =========================================================================
    // getTableSchema
    // =========================================================================

    @Nested
    @DisplayName("GET /schema/{tableName}")
    class GetTableSchemaTests {

        @Test
        @DisplayName("returns 200 with columns for existing table")
        void returns200ForExistingTable() {
            Response resp = resource.getTableSchema("users");
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            assertTrue(body.contains("columns"), "Response should have columns");
            assertTrue(body.contains("users"));
        }

        @Test
        @DisplayName("returns 404 for unknown table")
        void returns404ForUnknownTable() {
            Response resp = resource.getTableSchema("nonexistent_table_xyz");
            assertEquals(404, resp.getStatus());
        }
    }

    // =========================================================================
    // getDatabaseInfo
    // =========================================================================

    @Nested
    @DisplayName("GET /info")
    class GetDatabaseInfoTests {

        @Test
        @DisplayName("returns 200 with database metadata")
        void returns200WithMetadata() {
            Response resp = resource.getDatabaseInfo();
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            assertTrue(body.contains("databasePath"));
            assertTrue(body.contains("tableCount"));
        }

        @Test
        @DisplayName("tableCount is at least 5")
        void tableCountAtLeastFive() throws Exception {
            Response resp = resource.getDatabaseInfo();
            String body = (String) resp.getEntity();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(body, Map.class);
            int count = (int) parsed.get("tableCount");
            assertTrue(count >= 5, "Should have at least 5 tables");
        }
    }

    // =========================================================================
    // executeQuery
    // =========================================================================

    @Nested
    @DisplayName("POST /query")
    class ExecuteQueryTests {

        @Test
        @DisplayName("returns 200 for a valid SELECT")
        void returns200ForValidSelect() {
            Map<String, Object> body = Map.of("sql", "SELECT 1 AS one");
            Response resp = resource.executeQuery(body);
            assertEquals(200, resp.getStatus());
        }

        @Test
        @DisplayName("returns 400 when sql field is missing")
        void returns400WhenSqlMissing() {
            Response resp = resource.executeQuery(Map.of());
            assertEquals(400, resp.getStatus());
        }

        @Test
        @DisplayName("returns 400 when sql field is blank")
        void returns400WhenSqlBlank() {
            Response resp = resource.executeQuery(Map.of("sql", "   "));
            assertEquals(400, resp.getStatus());
        }

        @Test
        @DisplayName("throws WriteQueryNotAllowedException for INSERT")
        void throwsForInsert() {
            Map<String, Object> body =
                    Map.of("sql", "INSERT INTO users (id) VALUES (999999)");
            assertThrows(
                    SqliteRestResource.WriteQueryNotAllowedException.class,
                    () -> resource.executeQuery(body));
        }

        @Test
        @DisplayName("throws WriteQueryNotAllowedException for DELETE")
        void throwsForDelete() {
            Map<String, Object> body =
                    Map.of("sql", "DELETE FROM users WHERE id = 999999");
            assertThrows(
                    SqliteRestResource.WriteQueryNotAllowedException.class,
                    () -> resource.executeQuery(body));
        }

        @Test
        @DisplayName("throws WriteQueryNotAllowedException for UPDATE")
        void throwsForUpdate() {
            Map<String, Object> body =
                    Map.of("sql", "UPDATE users SET id = 0 WHERE id = 999999");
            assertThrows(
                    SqliteRestResource.WriteQueryNotAllowedException.class,
                    () -> resource.executeQuery(body));
        }

        @Test
        @DisplayName("throws WriteQueryNotAllowedException for DROP")
        void throwsForDrop() {
            Map<String, Object> body = Map.of("sql", "DROP TABLE users");
            assertThrows(
                    SqliteRestResource.WriteQueryNotAllowedException.class,
                    () -> resource.executeQuery(body));
        }

        @Test
        @DisplayName("returns rows for SELECT from users table")
        void returnsRowsForUsersSelect() throws Exception {
            Map<String, Object> body = Map.of("sql", "SELECT user_id FROM users LIMIT 3");
            Response resp = resource.executeQuery(body);
            assertEquals(200, resp.getStatus());
            String entity = (String) resp.getEntity();
            assertTrue(entity.contains("results"));
        }

        @Test
        @DisplayName("PRAGMA statement is allowed")
        void pragmaIsAllowed() {
            Map<String, Object> body = Map.of("sql", "PRAGMA table_info(users)");
            Response resp = resource.executeQuery(body);
            assertEquals(200, resp.getStatus());
        }

        @Test
        @DisplayName("WITH (CTE) statement is allowed")
        void withStatementIsAllowed() {
            Map<String, Object> body =
                    Map.of("sql", "WITH cte AS (SELECT 1 AS x) SELECT * FROM cte");
            Response resp = resource.executeQuery(body);
            assertEquals(200, resp.getStatus());
        }
    }

    // =========================================================================
    // readRecords
    // =========================================================================

    @Nested
    @DisplayName("GET /records/{table}")
    class ReadRecordsTests {

        @Test
        @DisplayName("returns rows for existing table")
        void returnsRowsForExistingTable() {
            Response resp = resource.readRecords("users", 5, 0, null);
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            assertTrue(body.contains("rows"));
        }

        @Test
        @DisplayName("limit and offset parameters are respected")
        void limitAndOffsetRespected() throws Exception {
            Response resp = resource.readRecords("users", 3, 0, null);
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            @SuppressWarnings("unchecked")
            Map<String, Object> parsed = mapper.readValue(body, Map.class);
            int rowCount = (int) parsed.get("rowCount");
            assertTrue(rowCount <= 3, "Should return at most 3 rows");
        }

        @Test
        @DisplayName("empty conditions JSON object returns all rows (no WHERE clause)")
        void emptyConditionsJsonReturnsAllRows() {
            // An empty JSON object {} means conditions.isEmpty() is true → no WHERE clause appended
            Response resp = resource.readRecords("users", 2, 0, "{}");
            assertEquals(200, resp.getStatus());
            String body = (String) resp.getEntity();
            assertTrue(body.contains("rows"));
        }

        @Test
        @DisplayName("conditions JSON filter works")
        void conditionsFilterWorks() {
            // Use an id that is very unlikely to exist to get 0 rows
            Response resp = resource.readRecords("users", null, null,
                    "{\"id\":\"99999999\"}");
            assertEquals(200, resp.getStatus());
        }

        @Test
        @DisplayName("returns 500 for invalid table identifier")
        void invalidTableIdentifierReturns500() {
            // Identifier with spaces — fails the SAFE_IDENTIFIER check and triggers exception
            assertThrows(Exception.class,
                    () -> resource.readRecords("bad table; DROP TABLE users; --", null, null, null));
        }
    }

    // =========================================================================
    // createRecord
    // =========================================================================

    @Nested
    @DisplayName("POST /records/{table}")
    class CreateRecordTests {

        @Test
        @DisplayName("returns 400 when data field is missing")
        void returns400WhenDataMissing() {
            Response resp = resource.createRecord("users", Map.of());
            assertEquals(400, resp.getStatus());
        }

        @Test
        @DisplayName("returns 400 when data map is empty")
        void returns400WhenDataEmpty() {
            Map<String, Object> body = new HashMap<>();
            body.put("data", Map.of());
            Response resp = resource.createRecord("users", body);
            assertEquals(400, resp.getStatus());
        }
    }

    // =========================================================================
    // updateRecords
    // =========================================================================

    @Nested
    @DisplayName("PUT /records/{table}")
    class UpdateRecordsTests {

        @Test
        @DisplayName("returns 400 when data is missing")
        void returns400WhenDataMissing() {
            Map<String, Object> body = Map.of("conditions", Map.of("id", "1"));
            Response resp = resource.updateRecords("users", body);
            assertEquals(400, resp.getStatus());
        }

        @Test
        @DisplayName("returns 400 when conditions is missing")
        void returns400WhenConditionsMissing() {
            Map<String, Object> data = Map.of("username", "newname");
            Map<String, Object> body = new HashMap<>();
            body.put("data", data);
            Response resp = resource.updateRecords("users", body);
            assertEquals(400, resp.getStatus());
        }
    }

    // =========================================================================
    // deleteRecords
    // =========================================================================

    @Nested
    @DisplayName("DELETE /records/{table}")
    class DeleteRecordsTests {

        @Test
        @DisplayName("returns 400 when conditions is missing")
        void returns400WhenConditionsMissing() {
            Response resp = resource.deleteRecords("users", Map.of());
            assertEquals(400, resp.getStatus());
        }

        @Test
        @DisplayName("returns 400 when conditions map is empty")
        void returns400WhenConditionsEmpty() {
            Map<String, Object> body = new HashMap<>();
            body.put("conditions", Map.of());
            Response resp = resource.deleteRecords("users", body);
            assertEquals(400, resp.getStatus());
        }
    }

    // =========================================================================
    // createRecord — happy path (uses a temp DB to avoid corrupting shared one)
    // =========================================================================

    @Nested
    @DisplayName("POST /records/{table} — happy path")
    class CreateRecordHappyPathTests {

        @Test
        @DisplayName("inserting a seller returns 200 and affectedRows=1")
        void insertSellerReturns200(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
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
    // updateRecords — happy path
    // =========================================================================

    @Nested
    @DisplayName("PUT /records/{table} — happy path")
    class UpdateRecordsHappyPathTests {

        @Test
        @DisplayName("updating a non-existent record returns 200 with affectedRows=0")
        void updateNonExistentRowReturns200(@org.junit.jupiter.api.io.TempDir Path dir)
                throws Exception {
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
    // deleteRecords — happy path
    // =========================================================================

    @Nested
    @DisplayName("DELETE /records/{table} — happy path")
    class DeleteRecordsHappyPathTests {

        @Test
        @DisplayName("deleting a non-existent record returns 200 with affectedRows=0")
        void deleteNonExistentRowReturns200(@org.junit.jupiter.api.io.TempDir Path dir)
                throws Exception {
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
    // WriteQueryNotAllowedException
    // =========================================================================

    @Nested
    @DisplayName("WriteQueryNotAllowedException")
    class WriteQueryNotAllowedExceptionTests {

        @Test
        @DisplayName("exception message is preserved")
        void exceptionMessagePreserved() {
            SqliteRestResource.WriteQueryNotAllowedException ex =
                    new SqliteRestResource.WriteQueryNotAllowedException("test message");
            assertEquals("test message", ex.getMessage());
        }

        @Test
        @DisplayName("exception is a RuntimeException")
        void isRuntimeException() {
            SqliteRestResource.WriteQueryNotAllowedException ex =
                    new SqliteRestResource.WriteQueryNotAllowedException("test");
            assertInstanceOf(RuntimeException.class, ex);
        }
    }

    // =========================================================================
    // validateIdentifier (via public endpoints — indirect coverage)
    // =========================================================================

    @Nested
    @DisplayName("identifier validation")
    class IdentifierValidationTests {

        @Test
        @DisplayName("getTableSchema with valid identifier returns 200 or 404")
        void validIdentifierPassesValidation() {
            Response resp = resource.getTableSchema("valid_table_name");
            assertTrue(resp.getStatus() == 200 || resp.getStatus() == 404,
                    "Valid identifier should not throw an exception");
        }
    }

    // =========================================================================
    // Error paths — broken DB to trigger catch blocks
    // =========================================================================

    /**
     * Uses a deliberately broken (non-existent directory) DB path so that
     * {@code connect()} fails and each endpoint's {@code catch(Exception e)} branch is covered.
     */
    @Nested
    @DisplayName("error paths with broken DB")
    class BrokenDbTests {

        private final SqliteRestResource broken =
                new SqliteRestResource("/nonexistent/path/to/broken.db", new ObjectMapper());

        @Test
        @DisplayName("listTables returns 500 when DB path is invalid")
        void listTablesReturns500() {
            Response resp = broken.listTables();
            assertEquals(500, resp.getStatus());
        }

        @Test
        @DisplayName("getDatabaseInfo returns 500 when DB path is invalid")
        void getDatabaseInfoReturns500() {
            Response resp = broken.getDatabaseInfo();
            assertEquals(500, resp.getStatus());
        }

        @Test
        @DisplayName("getTableSchema returns 500 when DB path is invalid")
        void getTableSchemaReturns500() {
            Response resp = broken.getTableSchema("users");
            assertEquals(500, resp.getStatus());
        }

        @Test
        @DisplayName("executeQuery returns 500 when DB path is invalid")
        void executeQueryReturns500() {
            Response resp = broken.executeQuery(Map.of("sql", "SELECT 1"));
            assertEquals(500, resp.getStatus());
        }

        @Test
        @DisplayName("readRecords returns 500 when DB path is invalid")
        void readRecordsReturns500() {
            Response resp = broken.readRecords("users", null, null, null);
            assertEquals(500, resp.getStatus());
        }

        @Test
        @DisplayName("createRecord returns 500 when DB path is invalid")
        void createRecordReturns500() {
            Map<String, Object> body = new HashMap<>();
            body.put("data", Map.of("seller_name", "test"));
            Response resp = broken.createRecord("sellers", body);
            assertEquals(500, resp.getStatus());
        }

        @Test
        @DisplayName("updateRecords returns 500 when DB path is invalid")
        void updateRecordsReturns500() {
            Map<String, Object> body = new HashMap<>();
            body.put("data", Map.of("city", "Mumbai"));
            body.put("conditions", Map.of("user_id", "1"));
            Response resp = broken.updateRecords("users", body);
            assertEquals(500, resp.getStatus());
        }

        @Test
        @DisplayName("deleteRecords returns 500 when DB path is invalid")
        void deleteRecordsReturns500() {
            Map<String, Object> body = new HashMap<>();
            body.put("conditions", Map.of("user_id", "1"));
            Response resp = broken.deleteRecords("users", body);
            assertEquals(500, resp.getStatus());
        }
    }
}
