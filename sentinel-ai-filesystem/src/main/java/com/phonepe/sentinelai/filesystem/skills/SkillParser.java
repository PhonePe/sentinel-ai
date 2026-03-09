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

package com.phonepe.sentinelai.filesystem.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Parser for SKILL.md files with YAML frontmatter
 */
@Slf4j
public class SkillParser {

    // Pattern to match YAML frontmatter: ---\n..yaml..\n---
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n(.*)$",
                                                                       Pattern.DOTALL);

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /**
     * Parse a SKILL.md file and associated resources
     */
    public AgentSkill parse(Path skillDirectory) throws IOException {
        if (!Files.isDirectory(skillDirectory)) {
            throw new IllegalArgumentException("Not a directory: " + skillDirectory);
        }

        final var skillFile = skillDirectory.resolve("SKILL.md");
        if (!Files.exists(skillFile)) {
            throw new IllegalArgumentException("SKILL.md not found in: " + skillDirectory);
        }

        final var content = Files.readString(skillFile);
        final var matcher = FRONTMATTER_PATTERN.matcher(content);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                                               "Invalid SKILL.md format: YAML frontmatter not found in " + skillFile);
        }

        final var yamlContent = matcher.group(1);
        final var markdownBody = matcher.group(2).trim();

        final var metadata = yamlMapper.readValue(yamlContent, SkillMetadata.class);

        // Validate name matches directory
        final var directoryName = skillDirectory.getFileName().toString();
        metadata.validateName(directoryName);

        return AgentSkill.builder()
                .metadata(metadata)
                .instructions(markdownBody)
                .skillDirectory(skillDirectory)
                .referenceFiles(scanSubdirectory(skillDirectory, "references"))
                .scriptFiles(scanSubdirectory(skillDirectory, "scripts"))
                .assetFiles(scanSubdirectory(skillDirectory, "assets"))
                .build();
    }

    /**
     * Parse only the metadata (for discovery phase)
     */
    public SkillMetadata parseMetadata(Path skillDirectory) throws IOException {
        final var skillFile = skillDirectory.resolve("SKILL.md");
        if (!Files.exists(skillFile)) {
            throw new IllegalArgumentException("SKILL.md not found in: " + skillDirectory);
        }

        final var content = Files.readString(skillFile);
        final var matcher = FRONTMATTER_PATTERN.matcher(content);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid SKILL.md format in " + skillFile);
        }

        final var yamlContent = matcher.group(1);
        final var metadata = yamlMapper.readValue(yamlContent, SkillMetadata.class);

        // Validate name
        final var directoryName = skillDirectory.getFileName().toString();
        metadata.validateName(directoryName);

        return metadata;
    }

    /**
     * Scan a subdirectory for files and return a map of filename -> path
     */
    private Map<String, Path> scanSubdirectory(Path skillDirectory, String subdirName) throws IOException {
        final var subdir = skillDirectory.resolve(subdirName);
        if (!Files.isDirectory(subdir)) {
            return Map.of();
        }

        final var files = new HashMap<String, Path>();
        try (Stream<Path> paths = Files.walk(subdir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
                        String relativePath = subdir.relativize(path).toString();
                        files.put(relativePath, path);
                    });
        }

        return files;
    }
}
