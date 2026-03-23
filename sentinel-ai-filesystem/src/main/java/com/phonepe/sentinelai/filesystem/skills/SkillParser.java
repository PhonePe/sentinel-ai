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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/** Parser for SKILL.md files with YAML frontmatter */
@Slf4j
public class SkillParser {

    private static final String FRONTMATTER_DELIMITER = "---";

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    /** Parse a SKILL.md file and associated resources */
    public AgentSkill parse(Path skillDirectory) throws IOException {
        if (!Files.isDirectory(skillDirectory)) {
            throw new IllegalArgumentException("Not a directory: " + skillDirectory);
        }

        final var skillFile = skillDirectory.resolve("SKILL.md");
        if (!Files.exists(skillFile)) {
            throw new IllegalArgumentException("SKILL.md not found in: " + skillDirectory);
        }

        final var content = Files.readString(skillFile);
        final var parsed = parseFrontmatter(content);

        if (parsed == null) {
            throw new IllegalArgumentException(
                    "Invalid SKILL.md format: YAML frontmatter not found in " + skillFile);
        }

        SkillMetadata metadata;
        try {
            metadata = yamlMapper.readValue(parsed[0], SkillMetadata.class);
        } catch (IOException e) {
            log.error(
                    "Error deserializing YAML frontmatter for skill at path: {}",
                    skillDirectory,
                    e);
            throw e;
        }

        // Validate name matches directory
        final var directoryName = skillDirectory.getFileName().toString();
        metadata.validateName(directoryName);

        return AgentSkill.builder()
                .metadata(metadata)
                .instructions(parsed[1])
                .skillDirectory(skillDirectory)
                .referenceFiles(scanSubdirectory(skillDirectory, "references"))
                .scriptFiles(scanSubdirectory(skillDirectory, "scripts"))
                .assetFiles(scanSubdirectory(skillDirectory, "assets"))
                .build();
    }

    /** Parse only the metadata (for discovery phase) */
    public SkillMetadata parseMetadata(Path skillDirectory) throws IOException {
        final var skillFile = skillDirectory.resolve("SKILL.md");
        if (!Files.exists(skillFile)) {
            throw new IllegalArgumentException("SKILL.md not found in: " + skillDirectory);
        }

        final var content = Files.readString(skillFile);
        final var parsed = parseFrontmatter(content);

        if (parsed == null) {
            throw new IllegalArgumentException("Invalid SKILL.md format in " + skillFile);
        }

        final var metadata = yamlMapper.readValue(parsed[0], SkillMetadata.class);

        // Validate name
        final var directoryName = skillDirectory.getFileName().toString();
        metadata.validateName(directoryName);

        return metadata;
    }

    /**
     * Parse YAML frontmatter from a SKILL.md file content.
     *
     * <p>The format is a line containing only {@code ---} at the start of the file, followed by
     * YAML content, followed by another line containing only {@code ---}. Everything after the
     * closing delimiter is the Markdown body.
     *
     * @param content the full file content
     * @return a two-element array: {@code [yamlContent, markdownBody]}, or {@code null} if the file
     *     does not contain valid frontmatter
     */
    private String[] parseFrontmatter(final String content) {
        final var lines = content.split("\n", -1);

        // File must start with a line that is exactly "---" (trim allows trailing whitespace)
        if (lines.length < 3 || !lines[0].trim().equals(FRONTMATTER_DELIMITER)) {
            return null;
        }

        // Find the closing --- line (first occurrence after the opening)
        int closingLine = -1;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].trim().equals(FRONTMATTER_DELIMITER)) {
                closingLine = i;
                break;
            }
        }

        if (closingLine == -1) {
            return null;
        }

        // YAML is everything between the opening and closing ---
        final var yaml = new StringBuilder();
        for (int i = 1; i < closingLine; i++) {
            if (i > 1) {
                yaml.append('\n');
            }
            yaml.append(lines[i]);
        }

        // Body is everything after the closing ---, trimmed
        final var body = new StringBuilder();
        for (int i = closingLine + 1; i < lines.length; i++) {
            if (i > closingLine + 1) {
                body.append('\n');
            }
            body.append(lines[i]);
        }

        return new String[] {yaml.toString(), body.toString().trim()};
    }

    /** Scan a subdirectory for files and return a map of filename -> path */
    private Map<String, Path> scanSubdirectory(Path skillDirectory, String subdirName)
            throws IOException {
        final var subdir = skillDirectory.resolve(subdirName);
        if (!Files.isDirectory(subdir)) {
            return Map.of();
        }

        final var files = new HashMap<String, Path>();
        try (Stream<Path> paths = Files.walk(subdir)) {
            paths.filter(Files::isRegularFile)
                    .forEach(
                            path -> {
                                String relativePath = subdir.relativize(path).toString();
                                files.put(relativePath, path);
                            });
        }

        return files;
    }
}
