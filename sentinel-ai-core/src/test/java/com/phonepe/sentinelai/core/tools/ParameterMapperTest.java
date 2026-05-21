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
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParameterMapperTest {

    static class TestToolBox implements ToolBox {

        @Tool("Greet a user")
        public String greet(
                            @JsonPropertyDescription("The user name") String name) {
            return "hello " + name;
        }

        @Tool("Inspect a single item")
        public String inspectItem(
                                  @JsonPropertyDescription("The item to inspect") Item item) {
            return item.id();
        }

        @Tool("Multi-param tool")
        public String multiParam(
                                 @JsonPropertyDescription("A list of tags") List<String> tags,
                                 @JsonPropertyDescription("A count") int count) {
            return "done";
        }

        @Override
        public String name() {
            return "test";
        }

        @Tool("Process a list of items")
        public String processItems(
                                   @JsonPropertyDescription("The list of items to process") List<Item> items) {
            return "ok";
        }
    }

    private final TestToolBox toolBox = new TestToolBox();

    private final ParameterMapper mapper = new ParameterMapper(JsonUtils.createMapper());

    record Item(
            String id,
            String value
    ) {
    }

    @Test
    void complexObjectParameterSchemaIsPopulated() {
        final var schema = schemaForTool("inspectItem");

        final var properties = (ObjectNode) schema.get("properties");
        assertNotNull(properties);

        final var itemParam = (ObjectNode) properties.get("item");
        assertNotNull(itemParam, "item parameter schema must be present");
        assertEquals("object", itemParam.get("type").asText());

        final var itemProperties = itemParam.get("properties");
        assertNotNull(itemProperties, "Item object schema should have properties");
        assertNotNull(itemProperties.get("id"), "Item schema should contain 'id'");
        assertNotNull(itemProperties.get("value"), "Item schema should contain 'value'");

        assertEquals("The item to inspect", itemParam.get("description").asText());
    }

    @Test
    void listOfObjectsParameterPopulatesItemsInSchema() {
        final var schema = schemaForTool("processItems");

        final var properties = (ObjectNode) schema.get("properties");
        assertNotNull(properties, "properties node must be present");

        final var itemsParam = (ObjectNode) properties.get("items");
        assertNotNull(itemsParam, "items parameter schema must be present");

        assertEquals("array", itemsParam.get("type").asText(), "List<Item> should produce type: array");

        final var itemsNode = itemsParam.get("items");
        assertNotNull(itemsNode, "items field (array element schema) must be populated for List<Item>");
        assertFalse(itemsNode.isMissingNode(), "items schema must not be missing");

        assertEquals("object", itemsNode.get("type").asText(), "List element should be type: object");

        final var itemProperties = itemsNode.get("properties");
        assertNotNull(itemProperties, "Item object should have properties");
        assertNotNull(itemProperties.get("id"), "Item schema should have 'id' property");
        assertNotNull(itemProperties.get("value"), "Item schema should have 'value' property");

        assertEquals("The list of items to process", itemsParam.get("description").asText());
    }

    @Test
    void listOfPrimitivesParameterPopulatesItemsInSchema() {
        final var schema = schemaForTool("multiParam");

        final var properties = (ObjectNode) schema.get("properties");
        assertNotNull(properties);

        final var tagsParam = (ObjectNode) properties.get("tags");
        assertNotNull(tagsParam, "tags parameter schema must be present");
        assertEquals("array", tagsParam.get("type").asText(), "List<String> should produce type: array");

        final var itemsNode = tagsParam.get("items");
        assertNotNull(itemsNode, "items field must be populated for List<String>");
        assertFalse(itemsNode.isMissingNode());
        assertEquals("string", itemsNode.get("type").asText(), "List<String> items should be type: string");

        assertEquals("A list of tags", tagsParam.get("description").asText());

        final var countParam = (ObjectNode) properties.get("count");
        assertNotNull(countParam, "count parameter schema must be present");
        assertTrue(countParam.get("type").asText().contains("integer") || countParam.get("type").isArray(),
                   "Integer param should have integer type");
        assertEquals("A count", countParam.get("description").asText());
    }

    @Test
    void parametersNodeHasCorrectStructure() {
        final var schema = schemaForTool("processItems");

        assertEquals("object", schema.get("type").asText());
        assertFalse(schema.get("additionalProperties").asBoolean());
        assertNotNull(schema.get("properties"));
        assertNotNull(schema.get("required"));
        assertTrue(schema.get("required").isArray());
        assertEquals(1, schema.get("required").size());
        assertEquals("items", schema.get("required").get(0).asText());
    }

    @Test
    void stringParameterSchemaHasDescriptionAndCorrectType() {
        final var schema = schemaForTool("greet");

        final var properties = (ObjectNode) schema.get("properties");
        assertNotNull(properties);

        final var nameParam = (ObjectNode) properties.get("name");
        assertNotNull(nameParam, "name parameter schema must be present");
        assertEquals("string", nameParam.get("type").asText());
        assertEquals("The user name", nameParam.get("description").asText());
    }

    private java.lang.reflect.Method getMethod(String name) {
        for (var m : TestToolBox.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("Method not found: " + name);
    }

    private ObjectNode schemaForTool(String methodName) {
        final var tools = toolBox.tools();
        final var tool = tools.get(
                                   ToolUtils.toolMetadata(TestToolBox.class.getSimpleName(), getMethod(methodName))
                                           .getFirst()
                                           .getId());
        assertNotNull(tool, "Tool not found for method: " + methodName);
        return (ObjectNode) tool.accept(mapper);
    }
}
