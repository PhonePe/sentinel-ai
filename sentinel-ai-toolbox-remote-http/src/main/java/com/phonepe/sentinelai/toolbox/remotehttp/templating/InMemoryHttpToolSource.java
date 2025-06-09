package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpCallSpec;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpTool;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolMetadata;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolSource;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An in-memory implementation of {@link HttpToolSource} that stores tools in a map.
 */
@Slf4j
public class InMemoryHttpToolSource extends TemplatizedHttpToolSource<InMemoryHttpToolSource> {

    private final Map<String, Map<String, TemplatizedHttpTool>> tools = new ConcurrentHashMap<>();

    public InMemoryHttpToolSource() {
        this(new HttpCallTemplateExpander(), JsonUtils.createMapper());
    }

    @Builder
    public InMemoryHttpToolSource(HttpCallTemplateExpander expander, ObjectMapper mapper) {
        super(expander, mapper);
    }

    @Override
    public InMemoryHttpToolSource register(String upstream, List<TemplatizedHttpTool> tool) {
        if(tool.isEmpty()) {
            log.warn("No tool provided for upstream {}", upstream);
            return this;
        }
        tools.compute(upstream,
                      (u, existing) -> {
                          final var toolMap = tool.stream()
                                  .collect(Collectors.toUnmodifiableMap(t -> t.getMetadata().getName(),
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
                .map(HttpTool::getMetadata)
                .toList();
    }

    @Override
    public HttpCallSpec resolve(String upstream, String toolName, String arguments) {
        return Optional.ofNullable(tools.getOrDefault(upstream, Map.of())
                .get(toolName))
                .map(tool -> expandTemplate(arguments, tool))
                .orElseThrow(() -> new IllegalArgumentException(
                        "No tool %s found for upstream %s".formatted(toolName, upstream)));
    }

    @Override
    public List<String> upstreams() {
        return List.copyOf(tools.keySet());
    }

}
