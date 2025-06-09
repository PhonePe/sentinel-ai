package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolMetadata;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolSource;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Utility class for loading external http tool configuration from YAML etc.
 */
@UtilityClass
public class HttpToolReaders {
    public record ConfiguredHttpTool(
            HttpToolMetadata metadata,
            HttpCallTemplate definition,
            ResponseTransformerConfig transformer
    ) {
    }

    public record ConfiguredUpstream(
            URL url,
            List<ConfiguredHttpTool> tools
    ) {
    }


    @SneakyThrows
    public static <S extends HttpToolSource<TemplatizedHttpTool, S>>
    void loadToolsFromYAML(
            Path path,
            final HttpToolSource<TemplatizedHttpTool, S> toolSource) {
        loadToolsFromYAMLContent(Files.readAllBytes(path), toolSource);
    }

    @SneakyThrows
    public static <S extends HttpToolSource<TemplatizedHttpTool, S>>
    void loadToolsFromYAMLContent(
            byte[] content,
            final HttpToolSource<TemplatizedHttpTool, S> toolSource) {
        final var yamlMapper = new YAMLMapper();
        yamlMapper.readValue(content,
                             new TypeReference<Map<String, ConfiguredUpstream>>() {
                             })
                .forEach(((upstream, configuredUpstream) ->
                        toolSource.register(upstream,
                                            configuredUpstream.tools()
                                                    .stream()
                                                    .map(tool -> new TemplatizedHttpTool(
                                                            tool.metadata(), tool.definition(), tool.transformer()))
                                                    .toList())));
    }
}
