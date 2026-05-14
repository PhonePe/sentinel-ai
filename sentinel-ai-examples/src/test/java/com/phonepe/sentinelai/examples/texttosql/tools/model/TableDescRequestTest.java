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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableDescRequestTest {

    @Test
    void allowsEmptyTableNames() {
        TableDescRequest req = new TableDescRequest(List.of());
        assertTrue(req.tableNames().isEmpty());
    }

    @Test
    void allowsNullTableNames() {
        TableDescRequest req = new TableDescRequest(null);
        assertNull(req.tableNames());
    }

    @Test
    void recordEquality() {
        TableDescRequest a = new TableDescRequest(List.of("users"));
        TableDescRequest b = new TableDescRequest(List.of("users"));
        assertEquals(a, b);
    }

    @Test
    void storesTableNames() {
        List<String> names = List.of("users", "orders");
        TableDescRequest req = new TableDescRequest(names);
        assertEquals(names, req.tableNames());
    }

    @Test
    void toStringIncludesTableNames() {
        TableDescRequest req = new TableDescRequest(List.of("catalog"));
        String s = req.toString();
        assertTrue(s.contains("catalog"));
    }
}
