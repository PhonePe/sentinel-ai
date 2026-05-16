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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registry for discovering and managing Agent Skills
 */
@Slf4j
public class FileBasedSkillRegistry implements SkillRegistry {

    private final String baseDir;
    private final SkillParser parser = new SkillParser();
    private final Map<String, SkillMetadata> skillCatalog = new LinkedHashMap<>();
    private final Map<String, AgentSkill> loadedSkills = new LinkedHashMap<>();
    private final Set<Path> skillDirectories = new HashSet<>();

    public FileBasedSkillRegistry(final String baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * Tries to resolve a skill directory path.
     * <ol>
     * <li>Uses the path as-is if it is already an existing directory.</li>
     * <li>Resolves relative to {@code baseDir}.</li>
     * <li>Resolves relative to the current working directory.</li>
     * </ol>
     *
     * @throws IllegalArgumentException if no valid directory can be found
     */
    private static Path expandSkillDir(String skillDir, String baseDir) {
        var path = Paths.get(skillDir);
        if (Files.isDirectory(path)) {
            return path.toAbsolutePath().normalize();
        }

        path = Paths.get(baseDir, skillDir);
        if (Files.isDirectory(path)) {
            return path.toAbsolutePath().normalize();
        }

        path = Paths.get(System.getProperty("user.dir"), skillDir);
        if (Files.isDirectory(path)) {
            return path.toAbsolutePath().normalize();
        }

        throw new IllegalArgumentException("Skills directory does not exist: " + skillDir);
    }

    @Override
    public void discoverSkills(List<String> skillsDirs, Collection<String> skillsToLoad) throws IOException {
        // Discover skills from all provided directories
        final var skillsFilter = Set.copyOf(Objects.requireNonNullElseGet(skillsToLoad, Set::<String>of));
        for (String dirPath : skillsDirs) {
            final var skillsDir = expandSkillDir(dirPath, baseDir);
            discoverSkills(skillsDir, skillsFilter);
        }
    }

    /**
     * Discover skills in a directory (Tier 1: load metadata only)
     */
    @Override
    public void discoverSkills(Path skillsDirectory, Set<String> skillsToLoad) throws IOException {
        if (!Files.isDirectory(skillsDirectory)) {
            log.warn("Skills directory does not exist: {}", skillsDirectory);
            return;
        }

        skillDirectories.add(skillsDirectory);

        try (Stream<Path> paths = Files.list(skillsDirectory)) {
            paths.filter(Files::isDirectory)
                    .filter(dir -> {
                        // Get last component of path and check if it's in skillsToLoad (if specified)
                        final var skillName = dir.getFileName().toString();
                        if (!skillsToLoad.isEmpty() && !skillsToLoad.contains(skillName)) {
                            log.info("Skipping skill {} as it's not in the specified skills to load", skillName);
                            return false;
                        }
                        return true;
                    })
                    .forEach(skillDir -> {
                        try {
                            final var metadata = parser.parseMetadata(skillDir);
                            skillCatalog.put(metadata.getName(), metadata);
                            log.info("Discovered skill: {} - {}", metadata.getName(), metadata.getDescription());
                        }
                        catch (Exception e) {
                            log.warn("Failed to parse skill in {}: {}", skillDir, e.getMessage());
                        }
                    });
        }
    }

    /**
     * Format catalog for injection into system prompt
     */
    @Override
    public String formatCatalog() {
        if (skillCatalog.isEmpty()) {
            return "No skills available.";
        }

        final var sb = new StringBuilder();
        sb.append("Available Skills:\n\n");

        skillCatalog.values().forEach(metadata -> sb.append(String.format("- **%s**: %s%n",
                                                                          metadata.getName(),
                                                                          metadata.getDescription())));
        return sb.toString();
    }

    /** Format all loaded skills as YAML with their names and descriptions */
    @Override
    public String formatLoadedSkillsAsYaml() {
        if (loadedSkills.isEmpty()) {
            return "loaded_skills: []\n";
        }

        final var sb = new StringBuilder();
        sb.append("loaded_skills:\n");
        loadedSkills
                .values()
                .forEach(
                         skill -> {
                             sb.append("  - name: ").append(skill.getName()).append("\n");
                             sb.append("    description: ")
                                     .append(skill.getDescription())
                                     .append("\n");
                         });
        return sb.toString();
    }

    /**
     * Get a loaded skill by name
     */
    public Optional<AgentSkill> getLoadedSkill(String skillName) {
        return Optional.ofNullable(loadedSkills.get(skillName));
    }

    /**
     * Get skill catalog (name + description only)
     */
    @Override
    public Map<String, String> getSkillCatalog() {
        return skillCatalog.entrySet().stream()
                .collect(Collectors.toMap(
                                          Map.Entry::getKey,
                                          e -> e.getValue().getDescription(),
                                          (a, b) -> a,
                                          LinkedHashMap::new));
    }

    /**
     * Get all discovered skill names
     */
    @Override
    public Set<String> getSkillNames() {
        return Collections.unmodifiableSet(skillCatalog.keySet());
    }

    /**
     * Check if any skills are available
     */
    public boolean hasSkills() {
        return !skillCatalog.isEmpty();
    }

    @Override
    public Optional<String> loadReferenceFile(String skillName, String referenceFilePath) throws IOException {
        final var skill = loadSkill(skillName);
        if (skill.isEmpty() || skill.get().getReferenceFiles() == null || skill.get().getReferenceFiles().isEmpty()) {
            return Optional.empty();
        }
        final var referenceFile = skill.get().getReferenceFiles().get(referenceFilePath);
        if (referenceFile == null) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(referenceFile));
    }

    /**
     * Load a specific skill by name (Tier 2: load full instructions)
     */
    @Override
    public Optional<AgentSkill> loadSkill(String skillName) {
        if (loadedSkills.containsKey(skillName)) {
            return Optional.of(loadedSkills.get(skillName));
        }

        if (!skillCatalog.containsKey(skillName)) {
            log.warn("Skill not found in catalog: {}", skillName);
            return Optional.empty();
        }

        // Find the skill directory
        for (Path skillsDir : skillDirectories) {
            final var skillDir = skillsDir.resolve(skillName);
            if (Files.isDirectory(skillDir)) {
                try {
                    final var skill = parser.parse(skillDir);
                    loadedSkills.put(skillName, skill);
                    log.info("Loaded skill: {}", skillName);
                    return Optional.of(skill);
                }
                catch (Exception e) {
                    log.error("Failed to load skill {}: {}", skillName, e.getMessage(), e);
                    return Optional.empty();
                }
            }
        }

        log.error("Skill directory not found for: {}", skillName);
        return Optional.empty();
    }

    /**
     * Load a single skill from an absolute path (for single-skill mode)
     */
    @Override
    public Optional<AgentSkill> loadSkillFromPath(String singleSkill) throws IOException {
        var skillPath = expandSkillDir(singleSkill, baseDir);

        if (!Files.isDirectory(skillPath)) {
            throw new IllegalArgumentException("Not a directory: " + skillPath);
        }

        final var skill = parser.parse(skillPath);
        final var skillName = skill.getName();

        skillCatalog.put(skillName, skill.getMetadata());
        loadedSkills.put(skillName, skill);

        log.info("Loaded skill from path: {} ({})", skillPath, skillName);
        return Optional.of(skill);
    }
}
