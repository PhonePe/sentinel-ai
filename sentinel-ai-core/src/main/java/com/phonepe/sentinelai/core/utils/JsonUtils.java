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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.github.victools.jsonschema.generator.MemberScope;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.generator.TypeScope;
import com.github.victools.jsonschema.module.jackson.JacksonModule;

import lombok.experimental.UtilityClass;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Uti
 */
@UtilityClass
public class JsonUtils {

    private static class JacksonTitleModule extends JacksonModule {
        @Override
        public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
            super.applyToConfigBuilder(builder);

            builder.forTypesInGeneral()
                    .withDescriptionResolver(super::resolveDescriptionForType);
            builder.forFields()
                    .withDescriptionResolver(super::resolveDescription)
                    .withRequiredCheck(x -> true); //Mark all fields/parameters as required
            builder.forMethods()
                    .withDescriptionResolver(super::resolveDescription);
        }

        @Override
        protected String resolveDescription(MemberScope<?, ?> member) {
            // skip description look-up, to avoid duplicating the title
            return null;
        }

        @Override
        protected String resolveDescriptionForType(TypeScope scope) {
            // skip description look-up, to avoid duplicating the title
            return null;
        }
    }

    public static JsonMapper configureMapper(JsonMapper mapper) {
        mapper.findAndRegisterModules()
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        return mapper;
    }

    public static JsonMapper createMapper() {
        final var mapper = new JsonMapper();
        return configureMapper(mapper);
    }

    public static boolean empty(final JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() || (node
                .isObject() && node.isEmpty()) || (node.isArray() && node
                        .isEmpty());
    }

    public static JsonNode schema(final Class<?> clazz) {
        return schemaFromReflectType(clazz);
    }

    public static JsonNode schema(final JavaType javaType) {
        return schemaFromReflectType(toReflectType(javaType));
    }

    public static JsonNode schemaForPrimitive(final Class<?> clazz,
                                              String fieldName,
                                              ObjectMapper mapper) {
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        final var fields = mapper.createArrayNode();
        schema.set("required", fields);
        final var propertiesNode = mapper.createObjectNode();
        schema.set("properties", propertiesNode);
        fields.add(mapper.createObjectNode().textNode(fieldName));
        propertiesNode.set(fieldName, schema(clazz));
        return schema;
    }

    private static JsonNode schemaFromReflectType(final Type type) {
        final var configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12,
                                                                   OptionPreset.PLAIN_JSON);
        final var config = configBuilder.without(
                                                 Option.EXTRA_OPEN_API_FORMAT_VALUES)
                .without(Option.FLATTENED_ENUMS_FROM_TOSTRING)
                .without(Option.SCHEMA_VERSION_INDICATOR)
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                .with(Option.STRICT_TYPE_INFO)
                .with(Option.INLINE_ALL_SCHEMAS)
                .with(new JacksonTitleModule())
                .build();
        final var generator = new SchemaGenerator(config);
        return generator.generateSchema(type);
    }

    /**
     * Converts a Jackson {@link JavaType} to a standard {@link java.lang.reflect.Type} that
     * classmate (used internally by victools' SchemaGenerator) can resolve. Jackson's concrete
     * subtypes (e.g. {@code CollectionType}, {@code SimpleType}) are not recognised by classmate,
     * so we reconstruct an equivalent {@link ParameterizedType} when type parameters are present,
     * or return the raw class otherwise.
     */
    private static Type toReflectType(final JavaType javaType) {
        final var rawClass = javaType.getRawClass();
        final var bindings = javaType.getBindings();
        final var typeParams = bindings.getTypeParameters();
        if (typeParams.isEmpty()) {
            return rawClass;
        }
        final var args = typeParams.stream()
                .map(JsonUtils::toReflectType)
                .toArray(Type[]::new);
        return new ParameterizedType() {
            @Override
            public Type[] getActualTypeArguments() {
                return args;
            }

            @Override
            public Type getOwnerType() {
                return null;
            }

            @Override
            public Type getRawType() {
                return rawClass;
            }
        };
    }
}
