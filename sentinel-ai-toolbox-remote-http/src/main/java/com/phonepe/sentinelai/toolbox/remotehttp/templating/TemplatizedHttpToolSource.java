package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import com.bazaarvoice.jolt.Chainr;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolSource;
import lombok.SneakyThrows;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * A source of {@link TemplatizedHttpTool} instances that can be used to create HTTP tools
 */
public abstract class TemplatizedHttpToolSource<S extends HttpToolSource<TemplatizedHttpTool, S>> implements HttpToolSource<TemplatizedHttpTool, S> {
    protected final HttpCallTemplateExpander expander;
    protected final ObjectMapper mapper;

    protected TemplatizedHttpToolSource(HttpCallTemplateExpander expander, ObjectMapper mapper) {
        this.expander = Objects.requireNonNullElseGet(expander, HttpCallTemplateExpander::new);
        this.mapper = Objects.requireNonNullElseGet(mapper, JsonUtils::createMapper);
    }

    @SneakyThrows
    protected HttpCallSpec expandTemplate(String arguments, TemplatizedHttpTool tool) {
        final var spec = expander.convert(tool.getTemplate(),
                                          mapper.readValue(arguments, new TypeReference<>() {}));
        final var transformation = tool.getResponseTransformations();
        if(transformation != null) {
            return switch (transformation.getType()) {
                case JOLT -> spec.withResponseTransformer(new JoltTransformer(transformation.getConfig(), mapper));
            };
        }
        return spec;
    }

    private static final class JoltTransformer implements UnaryOperator<String> {
        private final Chainr chainr;
        private final ObjectMapper mapper;

        @SneakyThrows
        @SuppressWarnings("rawtypes")
        public JoltTransformer(String config, ObjectMapper mapper) {
            this.chainr = Chainr.fromSpec(mapper.readValue(config, new TypeReference<List>() {}));
            this.mapper = mapper;
        }

        @Override
        @SneakyThrows
        public String apply(String body) {
            return mapper.writeValueAsString(chainr.transform(mapper.readValue(body, Object.class)));
        }
    }

}
