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

import com.phonepe.sentinelai.core.errors.ParameterValidationError;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenAIJsonSchemaValidatorTest {

    @Test
    void acceptsValidOpenAIObjectSchema() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        final var required = mapper.createArrayNode();
        required.add("name");
        schema.set("required", required);
        final var properties = mapper.createObjectNode();
        properties.set("name", mapper.createObjectNode().put("type", "string"));
        schema.set("properties", properties);

        assertDoesNotThrow(() -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsUnsupportedKeyword() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.set("required", mapper.createArrayNode());
        schema.set("properties", mapper.createObjectNode());
        schema.put("allOf", "invalid");

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsObjectSchemaWithoutAdditionalPropertiesFalse() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("required", mapper.createArrayNode());
        schema.set("properties", mapper.createObjectNode());

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsObjectSchemaWithoutProperties() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.set("required", mapper.createArrayNode());

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsObjectSchemaWithoutRequired() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.set("properties", mapper.createObjectNode());

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsObjectSchemaWithAdditionalPropertiesTrue() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", true);
        schema.set("required", mapper.createArrayNode());
        schema.set("properties", mapper.createObjectNode());

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void acceptsObjectSchemaWithEmptyPropertiesAndRequired() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.set("required", mapper.createArrayNode());
        schema.set("properties", mapper.createObjectNode());

        assertDoesNotThrow(() -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsRootSchemaWhenTypeIsNotObject() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "string");

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsObjectSchemaWhenRequiredDoesNotMatchProperties() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        final var required = mapper.createArrayNode();
        required.add("name");
        schema.set("required", required);
        final var properties = mapper.createObjectNode();
        properties.set("name", mapper.createObjectNode().put("type", "string"));
        properties.set("age", mapper.createObjectNode().put("type", "integer"));
        schema.set("properties", properties);

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsObjectSchemaWhenRequiredContainsUnknownField() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        final var required = mapper.createArrayNode();
        required.add("name");
        required.add("missing");
        schema.set("required", required);
        final var properties = mapper.createObjectNode();
        properties.set("name", mapper.createObjectNode().put("type", "string"));
        schema.set("properties", properties);

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsObjectSchemaWhenRequiredContainsNonStringValue() {
        final var mapper = JsonUtils.createMapper();
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        final var required = mapper.createArrayNode();
        required.add(1);
        schema.set("required", required);
        final var properties = mapper.createObjectNode();
        properties.set("name", mapper.createObjectNode().put("type", "string"));
        schema.set("properties", properties);

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsSchemaWhenNestingDepthExceedsTenLevels() {
        final var mapper = JsonUtils.createMapper();
        final var root = objectSchema(mapper);
        var current = root;
        for (int i = 1; i <= 10; i++) {
            final var child = objectSchema(mapper);
            current.withObject("properties").set("level" + i, child);
            current.withArray("required").add("level" + i);
            current = child;
        }

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(root));
    }

    @Test
    void rejectsSchemaWhenTotalPropertyCountExceedsFiveThousand() {
        final var mapper = JsonUtils.createMapper();
        final var schema = objectSchema(mapper);
        final var properties = schema.withObject("properties");
        final var required = schema.withArray("required");
        IntStream.range(0, 5001).forEach(i -> {
            final var name = "property" + i;
            properties.set(name, mapper.createObjectNode().put("type", "string"));
            required.add(name);
        });

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsSchemaWhenTotalStringLengthExceedsLimit() {
        final var mapper = JsonUtils.createMapper();
        final var schema = objectSchema(mapper);
        final var longName = "a".repeat(120_001);
        schema.withObject("properties").set(longName, mapper.createObjectNode().put("type", "string"));
        schema.withArray("required").add(longName);

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsSchemaWhenEnumValueCountExceedsOneThousand() {
        final var mapper = JsonUtils.createMapper();
        final var schema = objectSchema(mapper);
        final var enumSchema = mapper.createObjectNode();
        enumSchema.put("type", "string");
        final var enumValues = enumSchema.putArray("enum");
        IntStream.range(0, 1001).forEach(i -> enumValues.add("value" + i));
        schema.withObject("properties").set("status", enumSchema);
        schema.withArray("required").add("status");

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void rejectsLargeStringEnumWhenTextLengthExceedsLimit() {
        final var mapper = JsonUtils.createMapper();
        final var schema = objectSchema(mapper);
        final var enumSchema = mapper.createObjectNode();
        enumSchema.put("type", "string");
        final var enumValues = enumSchema.putArray("enum");
        IntStream.range(0, 251).forEach(i -> enumValues.add("x".repeat(60)));
        schema.withObject("properties").set("status", enumSchema);
        schema.withArray("required").add("status");

        assertThrows(ParameterValidationError.class,
                     () -> OpenAIJsonSchemaValidator.validate(schema));
    }

    @Test
    void acceptsLargeStringEnumAtLimit() {
        final var mapper = JsonUtils.createMapper();
        final var schema = objectSchema(mapper);
        final var enumSchema = mapper.createObjectNode();
        enumSchema.put("type", "string");
        final var enumValues = enumSchema.putArray("enum");
        IntStream.range(0, 250).forEach(i -> enumValues.add("x".repeat(60)));
        schema.withObject("properties").set("status", enumSchema);
        schema.withArray("required").add("status");

        assertDoesNotThrow(() -> OpenAIJsonSchemaValidator.validate(schema));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode objectSchema(
            com.fasterxml.jackson.databind.ObjectMapper mapper) {
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.putObject("properties");
        schema.putArray("required");
        return schema;
    }
}
