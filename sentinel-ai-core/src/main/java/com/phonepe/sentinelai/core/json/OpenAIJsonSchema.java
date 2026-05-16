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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Typed JSON Schema subset supported by OpenAI structured outputs.
 */
@Value
@With
@Builder(toBuilder = true)
public class OpenAIJsonSchema {

    @NonNull
    Type type;

    String title;
    String description;
    String format;
    String pattern;

    List<String> enumValues;
    String constValue;

    BigDecimal multipleOf;
    BigDecimal maximum;
    BigDecimal exclusiveMaximum;
    BigDecimal minimum;
    BigDecimal exclusiveMinimum;

    Integer minItems;
    Integer maxItems;

    @Builder.Default
    List<String> required = List.of();

    @Builder.Default
    Map<String, OpenAIJsonSchema> properties = Map.of();

    @Builder.Default
    List<OpenAIJsonSchema> anyOf = List.of();

    OpenAIJsonSchema items;

    public ObjectNode toObjectNode(ObjectMapper objectMapper) {
        final var node = objectMapper.createObjectNode();
        node.put("type", type.jsonValue);
        putIfNotBlank(node, "title", title);
        putIfNotBlank(node, "description", description);
        putIfNotBlank(node, "format", format);
        putIfNotBlank(node, "pattern", pattern);
        if (enumValues != null && !enumValues.isEmpty()) {
            final ArrayNode enumNode = objectMapper.createArrayNode();
            enumValues.forEach(enumNode::add);
            node.set("enum", enumNode);
        }
        if (constValue != null) {
            node.put("const", constValue);
        }
        putIfPresent(node, "multipleOf", multipleOf);
        putIfPresent(node, "maximum", maximum);
        putIfPresent(node, "exclusiveMaximum", exclusiveMaximum);
        putIfPresent(node, "minimum", minimum);
        putIfPresent(node, "exclusiveMinimum", exclusiveMinimum);
        putIfPresent(node, "minItems", minItems);
        putIfPresent(node, "maxItems", maxItems);

        if (type == Type.OBJECT) {
            final var propertiesNode = objectMapper.createObjectNode();
            if (properties != null) {
                properties.forEach((name, schema) -> propertiesNode.set(name, schema.toObjectNode(objectMapper)));
            }
            node.set("properties", propertiesNode);

            final var requiredNode = objectMapper.createArrayNode();
            if (required != null) {
                required.forEach(requiredNode::add);
            }
            node.set("required", requiredNode);

            node.put("additionalProperties", false);
        }

        if (type == Type.ARRAY && items != null) {
            node.set("items", items.toObjectNode(objectMapper));
        }

        if (anyOf != null && !anyOf.isEmpty()) {
            final var anyOfNode = objectMapper.createArrayNode();
            anyOf.forEach(schema -> anyOfNode.add(schema.toObjectNode(objectMapper)));
            node.set("anyOf", anyOfNode);
        }

        return node;
    }

    private static void putIfNotBlank(ObjectNode node,
                                      String field,
                                      String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value);
        }
    }

    private static void putIfPresent(ObjectNode node,
                                     String field,
                                     BigDecimal value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    private static void putIfPresent(ObjectNode node,
                                     String field,
                                     Integer value) {
        if (value != null) {
            node.put(field, value);
        }
    }

    public enum Type {
        STRING("string"),
        NUMBER("number"),
        INTEGER("integer"),
        BOOLEAN("boolean"),
        OBJECT("object"),
        ARRAY("array"),
        NULL("null");

        private final String jsonValue;

        Type(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        @Override
        public String toString() {
            return jsonValue;
        }
    }
}
