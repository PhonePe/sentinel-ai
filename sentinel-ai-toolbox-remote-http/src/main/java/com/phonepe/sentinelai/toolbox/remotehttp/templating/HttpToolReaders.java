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
    @SneakyThrows
    public static <S extends HttpToolSource<TemplatizedHttpTool, S>> void loadToolsFromYAML(Path path,
                                                                                            final HttpToolSource<TemplatizedHttpTool, S> toolSource) {
        loadToolsFromYAMLContent(Files.readAllBytes(path), toolSource);
    }

    @SneakyThrows
    public static <S extends HttpToolSource<TemplatizedHttpTool, S>> void loadToolsFromYAMLContent(byte[] content,
                                                                                                   final HttpToolSource<TemplatizedHttpTool, S> toolSource) {
        final var yamlMapper = new YAMLMapper();
        yamlMapper.readValue(content,
                             new TypeReference<Map<String, ConfiguredUpstream>>() {
                             })
                .forEach(((upstream, configuredUpstream) -> toolSource.register(
                                                                                upstream,
                                                                                configuredUpstream
                                                                                        .tools()
                                                                                        .stream()
                                                                                        .map(tool -> new TemplatizedHttpTool(tool
                                                                                                .metadata(),
                                                                                                                             tool.definition(),
                                                                                                                             tool.transformer()))
                                                                                        .toList())));
    }


    public record ConfiguredHttpTool(
            HttpToolMetadata metadata,
            HttpCallTemplate definition,
            ResponseTransformerConfig transformer) {
    }

    public record ConfiguredUpstream(
            URL url,
            List<ConfiguredHttpTool> tools) {
    }
}
