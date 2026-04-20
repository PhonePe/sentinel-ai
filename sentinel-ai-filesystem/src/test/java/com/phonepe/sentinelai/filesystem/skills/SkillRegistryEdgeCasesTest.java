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

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Additional edge-case tests for {@link SkillRegistry} covering paths that are not exercised by
 * {@link SkillRegistryTest}.
 */
@DisplayName("SkillRegistry edge cases")
class SkillRegistryEdgeCasesTest {

    private Path tempDir;
    private SkillRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("skill-registry-edge-");
        registry = new SkillRegistry();
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .forEach(
                        p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {
                                // best-effort cleanup
                            }
                        });
    }

    // =========================================================================
    // discoverSkills — catch block when skill dir is malformed
    // =========================================================================

    @Nested
    @DisplayName("discoverSkills — malformed skill directory")
    class DiscoverMalformedSkillTests {

        @Test
        @DisplayName("skips a directory whose SKILL.md is missing, continues with others")
        void skipsMalformedSkillAndContinues() throws IOException {
            // A directory with NO SKILL.md — parser will throw, triggering the catch block
            Files.createDirectories(tempDir.resolve("broken-skill"));

            // A valid skill alongside the broken one
            createSkill(tempDir, "good-skill", "Good skill description");

            registry.discoverSkills(tempDir, Set.of());

            // broken-skill should be silently skipped; good-skill should be loaded
            assertFalse(
                    registry.getSkillNames().contains("broken-skill"),
                    "Malformed skill directory should be skipped");
            assertTrue(
                    registry.getSkillNames().contains("good-skill"),
                    "Valid skill should still be discovered");
        }
    }

    // =========================================================================
    // loadSkill — skill is in catalog but its directory no longer exists
    // =========================================================================

    @Nested
    @DisplayName("loadSkill — catalog entry with no matching directory")
    class LoadSkillMissingDirTests {

        @Test
        @DisplayName("returns empty when skill directory has been removed after discovery")
        void returnsEmptyWhenSkillDirRemoved() throws IOException {
            createSkill(tempDir, "vanishing-skill", "Will disappear");
            registry.discoverSkills(tempDir, Set.of());

            // Remove the skill directory after discovery to simulate an out-of-sync catalog
            final Path skillDir = tempDir.resolve("vanishing-skill");
            Files.walk(skillDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(
                            p -> {
                                try {
                                    Files.delete(p);
                                } catch (IOException ignored) {
                                    // best-effort
                                }
                            });

            final var result = registry.loadSkill("vanishing-skill");
            assertFalse(
                    result.isPresent(),
                    "loadSkill should return empty when the skill directory has been deleted");
        }
    }

    // =========================================================================
    // loadSkill — directory exists but SKILL.md is corrupt (parse failure)
    // =========================================================================

    @Nested
    @DisplayName("loadSkill — SKILL.md present but corrupt")
    class LoadSkillCorruptFileTests {

        @Test
        @DisplayName("returns empty when the SKILL.md cannot be parsed at load time")
        void returnsEmptyWhenSkillMdCorrupt() throws IOException {
            // Discover with a valid SKILL.md so the name lands in the catalog
            createSkill(tempDir, "corrupt-skill", "Will be corrupted");
            registry.discoverSkills(tempDir, Set.of());

            // Overwrite SKILL.md with content that will fail the parser (no front-matter block)
            Files.writeString(tempDir.resolve("corrupt-skill").resolve("SKILL.md"), "not valid");

            final var result = registry.loadSkill("corrupt-skill");
            assertFalse(
                    result.isPresent(),
                    "loadSkill should return empty when SKILL.md cannot be parsed");
        }
    }

    // =========================================================================
    // formatLoadedSkillsAsYaml — with loaded skills
    // =========================================================================

    @Nested
    @DisplayName("formatLoadedSkillsAsYaml")
    class FormatLoadedSkillsAsYamlTests {

        @Test
        @DisplayName("returns empty-list YAML when no skills have been loaded")
        void returnsEmptyYamlWhenNoneLoaded() {
            final String yaml = registry.formatLoadedSkillsAsYaml();
            assertTrue(yaml.contains("loaded_skills: []"), "Should contain empty list marker");
        }

        @Test
        @DisplayName("includes skill name and description for every loaded skill")
        void includesNameAndDescriptionForLoadedSkills() throws IOException {
            createSkill(tempDir, "alpha-skill", "Alpha description");
            createSkill(tempDir, "beta-skill", "Beta description");
            registry.discoverSkills(tempDir, Set.of());

            registry.loadSkill("alpha-skill");
            registry.loadSkill("beta-skill");

            final String yaml = registry.formatLoadedSkillsAsYaml();
            assertTrue(yaml.contains("alpha-skill"), "YAML should mention alpha-skill");
            assertTrue(yaml.contains("Alpha description"), "YAML should mention alpha's description");
            assertTrue(yaml.contains("beta-skill"), "YAML should mention beta-skill");
            assertTrue(yaml.contains("Beta description"), "YAML should mention beta's description");
        }
    }

    // =========================================================================
    // loadSkillFromPath — path is a file, not a directory
    // =========================================================================

    @Nested
    @DisplayName("loadSkillFromPath — file instead of directory")
    class LoadSkillFromPathFileTests {

        @Test
        @DisplayName("throws IllegalArgumentException when the path points to a regular file")
        void throwsWhenPathIsAFile() throws IOException {
            final Path regularFile = tempDir.resolve("not-a-dir.txt");
            Files.writeString(regularFile, "I am a file, not a directory");

            assertThrows(
                    IllegalArgumentException.class,
                    () -> registry.loadSkillFromPath(regularFile),
                    "Should throw when path is a regular file");
        }
    }

    // =========================================================================
    // getLoadedSkill — before and after loading
    // =========================================================================

    @Nested
    @DisplayName("getLoadedSkill")
    class GetLoadedSkillTests {

        @Test
        @DisplayName("returns empty before any skill is loaded")
        void returnsEmptyBeforeLoad() {
            assertTrue(registry.getLoadedSkill("anything").isEmpty());
        }

        @Test
        @DisplayName("returns the skill after it has been loaded")
        void returnsSkillAfterLoad() throws IOException {
            createSkill(tempDir, "loaded-skill", "A loaded skill");
            registry.discoverSkills(tempDir, Set.of());
            registry.loadSkill("loaded-skill");

            assertTrue(registry.getLoadedSkill("loaded-skill").isPresent());
        }
    }

    // =========================================================================
    // AgentSkill.formatCatalogEntry
    // =========================================================================

    @Nested
    @DisplayName("AgentSkill.formatCatalogEntry")
    class AgentSkillFormatCatalogEntryTests {

        @Test
        @DisplayName("returns markdown formatted name and description")
        void returnsMarkdownFormattedEntry() {
            final SkillMetadata meta =
                    SkillMetadata.builder().name("my-skill").description("Does something").build();
            final AgentSkill skill =
                    AgentSkill.builder()
                            .metadata(meta)
                            .instructions("# Instructions")
                            .skillDirectory(tempDir)
                            .build();

            final String entry = skill.formatCatalogEntry();
            assertEquals("- **my-skill**: Does something", entry);
        }
    }

    // =========================================================================
    // helpers
    // =========================================================================

    private static void createSkill(Path baseDir, String name, String description)
            throws IOException {
        final Path skillDir = baseDir.resolve(name);
        Files.createDirectories(skillDir);
        Files.writeString(
                skillDir.resolve("SKILL.md"),
                """
                ---
                name: %s
                description: %s
                ---

                # %s Instructions
                """.formatted(name, description, name));
    }
}
