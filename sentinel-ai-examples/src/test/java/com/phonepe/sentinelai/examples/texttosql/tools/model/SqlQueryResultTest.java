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

package com.phonepe.sentinelai.examples.texttosql.tools.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SqlQueryResult")
class SqlQueryResultTest {

    @Test
    @DisplayName("record construction stores all fields correctly")
    void constructorStoresFields() {
        List<String> rows = List.of("{\"id\":1}", "{\"id\":2}");
        SqlQueryResult result = new SqlQueryResult("SELECT * FROM users", rows, "Found 2 users", 42L);

        assertEquals("SELECT * FROM users", result.generatedSql());
        assertEquals(rows, result.results());
        assertEquals("Found 2 users", result.explanation());
        assertEquals(42L, result.executionTimeMs());
    }

    @Test
    @DisplayName("record allows null explanation")
    void nullExplanationAllowed() {
        SqlQueryResult result = new SqlQueryResult("SELECT 1", List.of(), null, 0L);
        assertNull(result.explanation());
    }

    @Test
    @DisplayName("record allows empty results list")
    void emptyResultsAllowed() {
        SqlQueryResult result = new SqlQueryResult("SELECT 1", List.of(), "no rows", 5L);
        assertTrue(result.results().isEmpty());
    }

    @Test
    @DisplayName("record equality is value-based")
    void recordEquality() {
        List<String> rows = List.of("{\"x\":1}");
        SqlQueryResult a = new SqlQueryResult("SELECT 1", rows, "ok", 10L);
        SqlQueryResult b = new SqlQueryResult("SELECT 1", rows, "ok", 10L);
        assertEquals(a, b);
    }

    @Test
    @DisplayName("record hashCode is consistent with equals")
    void hashCodeConsistentWithEquals() {
        List<String> rows = List.of("{\"x\":1}");
        SqlQueryResult a = new SqlQueryResult("SELECT 1", rows, "ok", 10L);
        SqlQueryResult b = new SqlQueryResult("SELECT 1", rows, "ok", 10L);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("toString includes field values")
    void toStringIncludesFields() {
        SqlQueryResult result = new SqlQueryResult("SELECT 1", List.of(), "explanation", 99L);
        String s = result.toString();
        assertTrue(s.contains("SELECT 1"));
        assertTrue(s.contains("99"));
    }
}
