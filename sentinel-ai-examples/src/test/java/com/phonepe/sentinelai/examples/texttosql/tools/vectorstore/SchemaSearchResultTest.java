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

package com.phonepe.sentinelai.examples.texttosql.tools.vectorstore;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SchemaSearchResult")
class SchemaSearchResultTest {

    @Test
    @DisplayName("record stores all fields correctly")
    void storesAllFields() {
        SchemaSearchResult r = new SchemaSearchResult("table", "users", null, "users content", 0.9f);
        assertEquals("table", r.docType());
        assertEquals("users", r.tableName());
        assertNull(r.columnName());
        assertEquals("users content", r.content());
        assertEquals(0.9f, r.score(), 1e-6f);
    }

    @Test
    @DisplayName("record stores column-level result correctly")
    void storesColumnLevelResult() {
        SchemaSearchResult r =
                new SchemaSearchResult("column", "orders", "status", "orders status content", 0.75f);
        assertEquals("column", r.docType());
        assertEquals("orders", r.tableName());
        assertEquals("status", r.columnName());
        assertEquals("orders status content", r.content());
        assertEquals(0.75f, r.score(), 1e-6f);
    }

    @Test
    @DisplayName("record equality is value-based")
    void equalityIsValueBased() {
        SchemaSearchResult a = new SchemaSearchResult("table", "users", null, "content", 1.0f);
        SchemaSearchResult b = new SchemaSearchResult("table", "users", null, "content", 1.0f);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    @DisplayName("toString includes field values")
    void toStringIncludesFields() {
        SchemaSearchResult r = new SchemaSearchResult("table", "sellers", null, "sellers desc", 0.5f);
        String s = r.toString();
        assertTrue(s.contains("sellers"));
    }
}
