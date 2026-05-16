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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.json.OpenAIJsonSchema;
import com.phonepe.sentinelai.core.openai.OpenAISchemaProvider;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ParameterMapperTest {

    static class InvalidRegistrationTestToolBox {
        @Tool(value = "rejects invalid metadata map schema")
        public String invalidMapSchemaTool(@OpenAISchemaProvider("invalidMetadataSchema") Map<String, String> metadata) {
            return metadata.getOrDefault("tenantId", "missing");
        }

        OpenAIJsonSchema invalidMetadataSchema() {
            return OpenAIJsonSchema.builder()
                    .type(OpenAIJsonSchema.Type.OBJECT)
                    .properties(Map.of("tenantId",
                                       OpenAIJsonSchema.builder()
                                               .type(OpenAIJsonSchema.Type.OBJECT)
                                               .build()))
                    .required(List.of("tenantId"))
                    .build();
        }
    }

    static class RegistrationTestToolBox {
        @Tool(value = "accepts default metadata map")
        public String defaultMapSchemaTool(Map<String, String> metadata) {
            return metadata.getOrDefault("tenantId", "missing");
        }

        @Tool(value = "accepts metadata map")
        public String mapSchemaTool(@OpenAISchemaProvider("metadataSchema") Map<String, String> metadata) {
            return metadata.getOrDefault("tenantId", "missing");
        }

        OpenAIJsonSchema invalidMetadataSchema() {
            return OpenAIJsonSchema.builder()
                    .type(OpenAIJsonSchema.Type.OBJECT)
                    .properties(Map.of("tenantId",
                                       OpenAIJsonSchema.builder()
                                               .type(OpenAIJsonSchema.Type.OBJECT)
                                               .build()))
                    .required(List.of("tenantId"))
                    .build();
        }

        OpenAIJsonSchema metadataSchema() {
            return OpenAIJsonSchema.builder()
                    .type(OpenAIJsonSchema.Type.OBJECT)
                    .properties(Map.of("tenantId",
                                       OpenAIJsonSchema.builder()
                                               .type(OpenAIJsonSchema.Type.STRING)
                                               .build(),
                                       "region",
                                       OpenAIJsonSchema.builder()
                                               .type(OpenAIJsonSchema.Type.STRING)
                                               .build()))
                    .required(List.of("tenantId", "region"))
                    .build();
        }
    }

    static class TestToolBox {
        @Tool(value = "custom schema tool")
        public String customSchema(
                                   @JsonPropertyDescription("Name of the user") @OpenAISchemaProvider("nameSchema") String name,
                                   @OpenAISchemaProvider("ageSchema") Integer age) {
            return "ok";
        }

        @Tool(value = "default schema tool")
        public String defaultSchema(
                                    @JsonPropertyDescription("Name of the user") String name,
                                    Integer age) {
            return "ok";
        }

        @Tool(value = "invalid custom schema tool")
        public String invalidCustomSchema(
                                          @JsonPropertyDescription("Name of the user") @OpenAISchemaProvider("invalidNameSchema") String name) {
            return "ok";
        }

        OpenAIJsonSchema ageSchema() {
            return OpenAIJsonSchema.builder()
                    .type(OpenAIJsonSchema.Type.INTEGER)
                    .minimum(java.math.BigDecimal.valueOf(18))
                    .build();
        }

        OpenAIJsonSchema invalidNameSchema() {
            return OpenAIJsonSchema.builder()
                    .type(OpenAIJsonSchema.Type.STRING)
                    .anyOf(List.of(OpenAIJsonSchema.builder()
                            .type(OpenAIJsonSchema.Type.OBJECT)
                            .build()))
                    .build();
        }

        OpenAIJsonSchema nameSchema() {
            return OpenAIJsonSchema.builder()
                    .type(OpenAIJsonSchema.Type.STRING)
                    .enumValues(List.of("alice", "bob"))
                    .build();
        }
    }

    private static ParameterMapper parameterMapper(final ObjectMapper mapper) {
        try {
            final var constructor = ParameterMapper.class.getDeclaredConstructor(ObjectMapper.class);
            constructor.setAccessible(true);
            return constructor.newInstance(mapper);
        }
        catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void parametersFromMethodInfoAllowsNestedObjectSchemaInAnyOf() throws Exception {
        final var mapper = JsonUtils.createMapper();
        final var method = TestToolBox.class.getMethod("invalidCustomSchema", String.class);
        final var methodInfo = ToolUtils.toolMetadata("TestToolBox", method).getSecond();

        final var schema = ParameterMapper.parametersFromMethodInfo(mapper, methodInfo);
        final var nestedObjectSchema = schema.get("properties")
                .get("name")
                .get("anyOf")
                .get(0);

        assertEquals("object", nestedObjectSchema.get("type").asText());
        assertEquals(JsonNodeType.OBJECT, nestedObjectSchema.get("properties").getNodeType());
        assertEquals(JsonNodeType.ARRAY, nestedObjectSchema.get("required").getNodeType());
    }

    @Test
    void parametersFromMethodInfoFallsBackToMethodParameterSchema() throws Exception {
        final var mapper = JsonUtils.createMapper();
        final var method = TestToolBox.class.getMethod("defaultSchema", String.class, Integer.class);

        final var methodInfo = ToolUtils.toolMetadata("TestToolBox", method).getSecond();
        final var schema = ParameterMapper.parametersFromMethodInfo(mapper, methodInfo);

        final var properties = schema.get("properties");
        assertEquals("string", properties.get("name").get("type").asText());
        assertEquals("Name of the user", properties.get("name").get("description").asText());
        assertEquals("integer", properties.get("age").get("type").asText());
        assertFalse(properties.get("age").has("minimum"));
    }

    @Test
    void parametersFromMethodInfoUsesCustomFieldSchemaWhenPresent() throws Exception {
        final var mapper = JsonUtils.createMapper();
        final var method = TestToolBox.class.getMethod("customSchema", String.class, Integer.class);
        final var methodInfo = ToolUtils.toolMetadata("TestToolBox", method).getSecond();
        final var schema = ParameterMapper.parametersFromMethodInfo(mapper, methodInfo);

        final var properties = schema.get("properties");
        assertEquals("string", properties.get("name").get("type").asText());
        assertEquals("alice", properties.get("name").get("enum").get(0).asText());
        assertEquals("bob", properties.get("name").get("enum").get(1).asText());
        assertEquals(18, properties.get("age").get("minimum").asInt());
    }

    @Test
    void readToolsRegistersInternalToolWhenNestedObjectMapSchemaIsUsed() {
        final var tools = ToolUtils.readTools(new InvalidRegistrationTestToolBox());

        assertNotNull(tools.get("invalid_registration_test_tool_box_invalid_map_schema_tool"));
    }

    @Test
    void readToolsRegistersInternalToolWithCustomMapFieldSchema() {
        final var mapper = JsonUtils.createMapper();
        final var tools = ToolUtils.readTools(new RegistrationTestToolBox());

        final var internalTool = (InternalTool) tools.get("registration_test_tool_box_map_schema_tool");
        final var schema = ParameterMapper.parametersFromMethodInfo(mapper, internalTool.getMethodInfo());

        final var metadataSchema = schema.get("properties").get("metadata");
        assertEquals("object", metadataSchema.get("type").asText());
        assertEquals("string", metadataSchema.get("properties").get("tenantId").get("type").asText());
        assertEquals("string", metadataSchema.get("properties").get("region").get("type").asText());
        assertEquals(false, metadataSchema.get("additionalProperties").asBoolean());
    }

    @Test
    void readToolsRegistersInternalToolWithDefaultMapFieldSchema() {
        final var mapper = JsonUtils.createMapper();
        final var tools = ToolUtils.readTools(new RegistrationTestToolBox());

        final var internalTool = (InternalTool) tools.get("registration_test_tool_box_default_map_schema_tool");
        final var schema = ParameterMapper.parametersFromMethodInfo(mapper, internalTool.getMethodInfo());

        final var metadataSchema = schema.get("properties").get("metadata");
        assertEquals("object", metadataSchema.get("type").asText());
        assertEquals(false, metadataSchema.get("additionalProperties").asBoolean());
        assertEquals(JsonNodeType.OBJECT, metadataSchema.get("properties").getNodeType());
        assertEquals(JsonNodeType.ARRAY, metadataSchema.get("required").getNodeType());
    }

    @Test
    void visitExternalToolAddsEmptyPropertiesAndRequiredForObjectSchemas() {
        final var mapper = JsonUtils.createMapper();
        final var parameterSchema = mapper.createObjectNode();
        parameterSchema.put("type", "object");
        parameterSchema.put("additionalProperties", false);
        final var externalTool = new ExternalTool(ToolDefinition.builder()
                .id("external-tool")
                .name("external-tool")
                .description("external tool")
                .build(),
                                                  parameterSchema,
                                                  (ctx, callId, args) -> null);

        final var schema = (ObjectNode) parameterMapper(mapper).visit(externalTool);

        assertEquals("object", schema.get("type").asText());
        assertEquals(JsonNodeType.OBJECT, schema.get("properties").getNodeType());
        assertEquals(JsonNodeType.ARRAY, schema.get("required").getNodeType());
    }
}
