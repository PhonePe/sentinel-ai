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

package com.phonepe.sentinelai.core.json;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIJsonSchemaTest {

    @Test
    void alwaysSerializesObjectPropertiesAndRequired() {
        final var mapper = JsonUtils.createMapper();
        final var schema = OpenAIJsonSchema.builder()
                .type(OpenAIJsonSchema.Type.OBJECT)
                .build();

        final var node = schema.toObjectNode(mapper);

        assertTrue(node.get("properties").isObject());
        assertTrue(node.get("required").isArray());
        assertFalse(node.get("additionalProperties").asBoolean());
    }

    @Test
    void serializesAnyOfAndArrayItemsWithTypedSchemas() {
        final var mapper = JsonUtils.createMapper();
        final var schema = OpenAIJsonSchema.builder()
                .type(OpenAIJsonSchema.Type.ARRAY)
                .items(OpenAIJsonSchema.builder().type(OpenAIJsonSchema.Type.STRING).build())
                .anyOf(List.of(OpenAIJsonSchema.builder()
                        .type(OpenAIJsonSchema.Type.ARRAY)
                        .items(OpenAIJsonSchema.builder().type(OpenAIJsonSchema.Type.STRING).build())
                        .build(),
                               OpenAIJsonSchema.builder().type(OpenAIJsonSchema.Type.NULL).build()))
                .build();

        final var node = schema.toObjectNode(mapper);

        assertEquals("array", node.get("type").asText());
        assertEquals("string", node.get("items").get("type").asText());
        assertEquals(2, node.get("anyOf").size());
        assertEquals("array", node.get("anyOf").get(0).get("type").asText());
        assertEquals("null", node.get("anyOf").get(1).get("type").asText());
    }

    @Test
    void serializesOpenAIStructuredOutputSubset() {
        final var mapper = JsonUtils.createMapper();

        final var schema = OpenAIJsonSchema.builder()
                .type(OpenAIJsonSchema.Type.OBJECT)
                .title("Person")
                .description("Structured person output")
                .properties(Map.of("name",
                                   OpenAIJsonSchema.builder()
                                           .type(OpenAIJsonSchema.Type.STRING)
                                           .build(),
                                   "age",
                                   OpenAIJsonSchema.builder()
                                           .type(OpenAIJsonSchema.Type.INTEGER)
                                           .minimum(BigDecimal.valueOf(18))
                                           .build()))
                .required(List.of("name", "age"))
                .build();

        final var node = schema.toObjectNode(mapper);

        assertEquals("object", node.get("type").asText());
        assertEquals("string", node.get("properties").get("name").get("type").asText());
        assertEquals(18, node.get("properties").get("age").get("minimum").asInt());
        assertTrue(node.get("required").isArray());
        assertFalse(node.get("additionalProperties").asBoolean());
    }
}
