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

import com.fasterxml.jackson.databind.JsonNode;

import com.phonepe.sentinelai.core.errors.ParameterValidationError;

import lombok.experimental.UtilityClass;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Validator for the OpenAI structured output JSON Schema subset.
 */
@UtilityClass
public class OpenAIJsonSchemaValidator {
    private static final int MAX_OBJECT_PROPERTIES = 5000;
    private static final int MAX_NESTING_DEPTH = 10;
    private static final int MAX_TOTAL_STRING_LENGTH = 120_000;
    private static final int MAX_ENUM_VALUES = 1000;
    private static final int MAX_SINGLE_ENUM_STRING_LENGTH = 15_000;
    private static final int SINGLE_ENUM_STRING_LENGTH_THRESHOLD = 250;

    private static final Set<String> SUPPORTED_KEYS = Set.of("type",
                                                             "title",
                                                             "description",
                                                             "format",
                                                             "pattern",
                                                             "enum",
                                                             "const",
                                                             "multipleOf",
                                                             "maximum",
                                                             "exclusiveMaximum",
                                                             "minimum",
                                                             "exclusiveMinimum",
                                                             "minItems",
                                                             "maxItems",
                                                             "required",
                                                             "properties",
                                                             "anyOf",
                                                             "items",
                                                             "additionalProperties");

    private static final class ValidationStats {
        private int totalProperties;
        private int totalStringLength;
        private int totalEnumValues;

        private void addStringLength(String value,
                                     String kind,
                                     String path) {
            totalStringLength += value.length();
            if (totalStringLength > MAX_TOTAL_STRING_LENGTH) {
                throw new ParameterValidationError("Schema exceeds maximum total string length of 120000 characters at "
                        + path + " due to " + kind);
            }
        }
    }

    public static void validate(JsonNode schema) {
        validate(schema, true, "$", 1, new ValidationStats());
    }

    public static void validateFieldSchema(JsonNode schema) {
        validate(schema, false, "$", 1, new ValidationStats());
    }

    private static boolean isObjectSchema(JsonNode schema) {
        return schema.has("type") && schema.get("type").isTextual() && "object".equals(schema.get("type").asText());
    }

    private static void validate(JsonNode schema,
                                 boolean root,
                                 String path,
                                 int depth,
                                 ValidationStats stats) {
        if (schema == null || !schema.isObject()) {
            throw new ParameterValidationError("Schema at " + path + " must be a JSON object");
        }
        if (depth > MAX_NESTING_DEPTH) {
            throw new ParameterValidationError("Schema exceeds maximum nesting depth of 10 at " + path);
        }
        validateSupportedKeys(schema, path);
        validateConstValue(schema, path, stats);
        validateEnum(schema, path, stats);
        if (root) {
            final var type = schema.get("type");
            if (type == null || !type.isTextual() || !"object".equals(type.asText())) {
                throw new ParameterValidationError("Root schema must have type 'object'");
            }
            if (schema.has("anyOf")) {
                throw new ParameterValidationError("Root schema must not use anyOf");
            }
        }

        if (isObjectSchema(schema)) {
            if (!schema.has("properties")) {
                throw new ParameterValidationError("Object schema at " + path + " must define properties");
            }
            final var properties = schema.get("properties");
            if (!properties.isObject()) {
                throw new ParameterValidationError("properties at " + path + " must be an object");
            }
            validatePropertyLimits(properties, path, stats);
            if (!schema.has("required") || !schema.get("required").isArray()) {
                throw new ParameterValidationError("Object schema at " + path + " must define required fields");
            }
            validateRequiredFields(schema.get("required"), properties, path);
            if (!schema.has("additionalProperties") || !schema.get("additionalProperties").isBoolean()
                    || schema.get("additionalProperties").asBoolean()) {
                throw new ParameterValidationError("Object schema at " + path
                        + " must set additionalProperties to false");
            }
            final Iterator<String> propertyNames = properties.fieldNames();
            while (propertyNames.hasNext()) {
                final var propertyName = propertyNames.next();
                validate(properties.get(propertyName), false, path + ".properties." + propertyName, depth + 1, stats);
            }
        }

        if (schema.has("items")) {
            validate(schema.get("items"), false, path + ".items", depth + 1, stats);
        }

        if (schema.has("anyOf")) {
            final var anyOf = schema.get("anyOf");
            if (!anyOf.isArray() || anyOf.isEmpty()) {
                throw new ParameterValidationError("anyOf at " + path + " must be a non-empty array");
            }
            for (int i = 0; i < anyOf.size(); i++) {
                validate(anyOf.get(i), false, path + ".anyOf[" + i + "]", depth + 1, stats);
            }
        }
    }

    private static void validateConstValue(JsonNode schema,
                                           String path,
                                           ValidationStats stats) {
        if (schema.has("const") && schema.get("const").isTextual()) {
            stats.addStringLength(schema.get("const").asText(), "const value", path);
        }
    }

    private static void validateEnum(JsonNode schema,
                                     String path,
                                     ValidationStats stats) {
        if (!schema.has("enum")) {
            return;
        }
        final var enumValues = schema.get("enum");
        if (!enumValues.isArray()) {
            throw new ParameterValidationError("enum at " + path + " must be an array");
        }

        int textualEnumLength = 0;
        boolean allTextual = true;
        for (int i = 0; i < enumValues.size(); i++) {
            final var enumValue = enumValues.get(i);
            stats.totalEnumValues++;
            if (stats.totalEnumValues > MAX_ENUM_VALUES) {
                throw new ParameterValidationError("Schema exceeds maximum of 1000 enum values at " + path);
            }
            if (enumValue.isTextual()) {
                final var value = enumValue.asText();
                textualEnumLength += value.length();
                stats.addStringLength(value, "enum value", path);
            }
            else {
                allTextual = false;
            }
        }

        if (allTextual && enumValues.size() > SINGLE_ENUM_STRING_LENGTH_THRESHOLD
                && textualEnumLength > MAX_SINGLE_ENUM_STRING_LENGTH) {
            throw new ParameterValidationError("String enum at " + path
                    + " exceeds maximum total length of 15000 characters");
        }
    }

    private static void validatePropertyLimits(JsonNode properties,
                                               String path,
                                               ValidationStats stats) {
        final Iterator<String> fieldNames = properties.fieldNames();
        while (fieldNames.hasNext()) {
            final var fieldName = fieldNames.next();
            stats.totalProperties++;
            if (stats.totalProperties > MAX_OBJECT_PROPERTIES) {
                throw new ParameterValidationError("Schema exceeds maximum of 5000 object properties at " + path);
            }
            stats.addStringLength(fieldName, "property name", path);
        }
    }

    private static void validateRequiredFields(JsonNode required,
                                               JsonNode properties,
                                               String path) {
        final var requiredFields = new LinkedHashSet<String>();
        for (int i = 0; i < required.size(); i++) {
            final var requiredField = required.get(i);
            if (!requiredField.isTextual()) {
                throw new ParameterValidationError("required at " + path + " must contain only field names");
            }
            requiredFields.add(requiredField.asText());
        }

        final var propertyNames = new LinkedHashSet<String>();
        final Iterator<String> fieldNames = properties.fieldNames();
        while (fieldNames.hasNext()) {
            propertyNames.add(fieldNames.next());
        }

        if (!requiredFields.equals(propertyNames)) {
            throw new ParameterValidationError("required at " + path
                    + " must exactly match property keys");
        }
    }

    private static void validateSupportedKeys(JsonNode schema,
                                              String path) {
        final Iterator<String> fieldNames = schema.fieldNames();
        while (fieldNames.hasNext()) {
            final var fieldName = fieldNames.next();
            if (!SUPPORTED_KEYS.contains(fieldName)) {
                throw new ParameterValidationError("Unsupported OpenAI JSON Schema keyword '" + fieldName
                        + "' at " + path);
            }
        }
    }
}
