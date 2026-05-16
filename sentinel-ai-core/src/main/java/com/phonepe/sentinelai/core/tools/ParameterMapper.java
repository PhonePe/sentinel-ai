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

package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.primitives.Primitives;

import com.phonepe.sentinelai.core.json.OpenAIJsonSchema;
import com.phonepe.sentinelai.core.json.OpenAIJsonSchemaValidator;
import com.phonepe.sentinelai.core.utils.Pair;

import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

import static com.phonepe.sentinelai.core.utils.JsonUtils.schema;
import static java.util.stream.Collectors.toMap;

/**
 *
 */
@AllArgsConstructor
public class ParameterMapper implements ExecutableToolVisitor<JsonNode> {
    private final ObjectMapper objectMapper;

    public static ObjectNode parametersFromMethodInfo(final ObjectMapper objectMapper,
                                                      final ToolMethodInfo methodInfo) {
        final Map<String, OpenAIJsonSchema> paramSchemas = methodInfo.parameters().stream().map(param -> {
            final var rawType = param.getType().getRawClass();
            OpenAIJsonSchema paramSchema;
            final var customFieldSchema = methodInfo.fieldSchemas().get(param.getName());
            if (customFieldSchema != null) {
                paramSchema = customFieldSchema;
            }
            else {
                paramSchema = objectMapper.convertValue(schema(rawType), OpenAIJsonSchema.class);
            }
            if (rawType.isAssignableFrom(String.class) || Primitives.isWrapperType(rawType)) {
                paramSchema = paramSchema.withDescription(param.getDescription());
            }
            return Pair.of(param.getName(), paramSchema);
        }).collect(toMap(Pair::getFirst, Pair::getSecond));
        final var schema = OpenAIJsonSchema.builder()
                .type(OpenAIJsonSchema.Type.OBJECT)
                .properties(paramSchemas)
                .required(List.copyOf(paramSchemas.keySet()))
                .build();
        return normalizeObjectSchema(objectMapper, schema.toObjectNode(objectMapper));
    }

    @Override
    public JsonNode visit(ExternalTool externalTool) {
        final var parameterSchema = externalTool.getParameterSchema();
        if (parameterSchema instanceof ObjectNode objectNode) {
            return normalizeObjectSchema(objectMapper, objectNode);
        }
        return parameterSchema; //Could be anything really, so we expect the toolbox to generate this
        // and keep it ready
    }

    @Override
    public JsonNode visit(InternalTool internalTool) {
        final var methodInfo = internalTool.getMethodInfo();
        return parametersFromMethodInfo(objectMapper, methodInfo);
    }

    private static ObjectNode normalizeObjectSchema(final ObjectMapper objectMapper,
                                                    final ObjectNode schemaNode) {
        if (schemaNode.has("type") && schemaNode.get("type").isTextual()
                && OpenAIJsonSchema.Type.OBJECT.toString().equals(schemaNode.get("type").asText())) {
            if (!schemaNode.has("properties") || schemaNode.get("properties").isNull()) {
                schemaNode.set("properties", objectMapper.createObjectNode());
            }
            if (!schemaNode.has("required") || schemaNode.get("required").isNull()) {
                schemaNode.set("required", objectMapper.createArrayNode());
            }
        }

        if (schemaNode.has("properties") && schemaNode.get("properties").isObject()) {
            final var properties = (ObjectNode) schemaNode.get("properties");
            properties.properties().forEach(entry -> {
                if (entry.getValue().isObject()) {
                    normalizeObjectSchema(objectMapper, (ObjectNode) entry.getValue());
                }
            });
        }

        if (schemaNode.has("items") && schemaNode.get("items").isObject()) {
            normalizeObjectSchema(objectMapper, (ObjectNode) schemaNode.get("items"));
        }

        if (schemaNode.has("anyOf") && schemaNode.get("anyOf").isArray()) {
            schemaNode.get("anyOf").forEach(child -> {
                if (child.isObject()) {
                    normalizeObjectSchema(objectMapper, (ObjectNode) child);
                }
            });
        }
        return schemaNode;
    }

}
