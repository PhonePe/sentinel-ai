package com.phonepe.sentinelai.toolbox.remotehttp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplateExpander;
import lombok.Builder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An in-memory implementation of {@link HttpToolSource} that stores tools in a map.
 */
@Slf4j
public class InMemoryHttpToolSource implements HttpToolSource<InMemoryHttpToolSource> {

    private final Map<String, Map<String, HttpTool>> tools = new ConcurrentHashMap<>();

    private final HttpCallTemplateExpander expander;
    private final ObjectMapper mapper;


    public InMemoryHttpToolSource() {
        this(new HttpCallTemplateExpander(), JsonUtils.createMapper());
    }

    @Builder
    public InMemoryHttpToolSource(HttpCallTemplateExpander expander, ObjectMapper mapper) {
        this.expander = Objects.requireNonNullElseGet(expander, HttpCallTemplateExpander::new);
        this.mapper = Objects.requireNonNullElseGet(mapper, JsonUtils::createMapper);
    }

    @Override
    public InMemoryHttpToolSource register(String upstream, List<HttpTool> tool) {
        if(tool.isEmpty()) {
            log.warn("No tool provided for upstream {}", upstream);
            return this;
        }
        tools.compute(upstream,
                      (u, existing) -> {
                          final var toolMap = tool.stream()
                                  .collect(Collectors.toUnmodifiableMap(t -> t.getToolConfig().getName(),
                                                            Function.identity()));
                            if (existing == null) {
                                return new ConcurrentHashMap<>(toolMap);
                            }
                            existing.putAll(toolMap);
                            return existing;
                      });
        return this;
    }

    @Override
    public List<HttpToolMetadata> list(String upstream) {
        return tools.getOrDefault(upstream, Map.of())
                .values()
                .stream()
                .map(HttpTool::getToolConfig)
                .toList();
    }

    @Override
    public HttpRemoteCallSpec resolve(String upstream, String toolName, String arguments) {
        return Optional.ofNullable(tools.getOrDefault(upstream, Map.of())
                .get(toolName))
                .map(tool -> expandTemplate(arguments, tool))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No tool %s found for upstream %s".formatted(toolName, upstream)));
    }

    @SneakyThrows
    private HttpRemoteCallSpec expandTemplate(String arguments, HttpTool tool) {
        return expander.convert(tool.getTemplate(),
                                mapper.readValue(arguments, new TypeReference<>() {}));
    }
}
