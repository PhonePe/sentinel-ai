package com.phonepe.sentinelai.core.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
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

/**
 *
 */
@UtilityClass
public class JsonUtils {

    public static JsonMapper createMapper() {
        final var mapper = new JsonMapper()      ;
        mapper.findAndRegisterModules()
                .setSerializationInclusion(JsonInclude.Include.NON_EMPTY)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
        return mapper;
    }

    private static class JacksonTitleModule extends JacksonModule {
        @Override
        public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
            super.applyToConfigBuilder(builder);

            builder.forTypesInGeneral().withDescriptionResolver(super::resolveDescriptionForType);
            builder.forFields().withDescriptionResolver(super::resolveDescription)
//                    .withRequiredCheck(super::getRequiredCheckBasedOnJsonPropertyAnnotation);
                    .withRequiredCheck(x -> true); //Mark all fields/parameters as required
            builder.forMethods().withDescriptionResolver(super::resolveDescription);
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

    public static JsonNode schema(final Class<?> clazz) {
        final var configBuilder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON);
        final var config = configBuilder
                .without(Option.EXTRA_OPEN_API_FORMAT_VALUES)
                .without(Option.FLATTENED_ENUMS_FROM_TOSTRING)
                .without(Option.SCHEMA_VERSION_INDICATOR)
                .with(Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT)
                .with(Option.STRICT_TYPE_INFO)
                .with(Option.INLINE_ALL_SCHEMAS)
                .with(new JacksonTitleModule())
                .build();
        final var generator = new SchemaGenerator(config);
        return generator.generateSchema(clazz);
    }
}
