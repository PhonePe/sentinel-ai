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

package com.phonepe.sentinelai.core.utils;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilsTest {

    @Test
    void testCreateMapper() {
        var mapper = JsonUtils.createMapper();
        assertNotNull(mapper);
        // Should not fail on unknown properties
        assertTrue(mapper.isEnabled(com.fasterxml.jackson.databind.MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS));
    }

    @Test
    void testEmptyWithEmptyArrayNode() {
        var mapper = JsonUtils.createMapper();
        var arrayNode = mapper.createArrayNode();
        assertTrue(JsonUtils.empty(arrayNode));
    }

    @Test
    void testEmptyWithEmptyObjectNode() {
        var mapper = JsonUtils.createMapper();
        final var node = mapper.createObjectNode();
        assertTrue(JsonUtils.empty(node));
    }

    @Test
    void testEmptyWithNonEmptyArrayNode() {
        var mapper = JsonUtils.createMapper();
        var arrayNode = mapper.createArrayNode();
        arrayNode.add("item");
        assertFalse(JsonUtils.empty(arrayNode));
    }

    @Test
    void testEmptyWithNonEmptyNode() {
        var mapper = JsonUtils.createMapper();
        final var node = mapper.createObjectNode();
        node.put("key", "value");
        assertFalse(JsonUtils.empty(node));
    }

    @Test
    void testEmptyWithNullNode() {
        assertTrue(JsonUtils.empty(null));
        assertTrue(JsonUtils.empty(NullNode.getInstance()));
        assertTrue(JsonUtils.empty(MissingNode.getInstance()));
    }

}
