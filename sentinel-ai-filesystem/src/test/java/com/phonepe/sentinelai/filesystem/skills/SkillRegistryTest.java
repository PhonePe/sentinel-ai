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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRegistryTest {

    private Path tempDir;
    private SkillRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("skill-registry-test-");
        registry = new SkillRegistry();
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.delete(path);
            }
            catch (IOException e) {
                // Ignore
            }
        });
    }

    @Test
    void testDiscoverSkills() throws IOException {
        createTestSkill("skill-one", "First test skill");
        createTestSkill("skill-two", "Second test skill");

        registry.discoverSkills(tempDir, Set.of());

        assertEquals(2, registry.getSkillNames().size());
        assertTrue(registry.getSkillNames().contains("skill-one"));
        assertTrue(registry.getSkillNames().contains("skill-two"));
        assertTrue(registry.hasSkills());
    }

    @Test
    void testDiscoverSkillsNonExistentDirectory() throws IOException {
        // Discovering a non-existent directory should not throw; just warn and skip
        final var nonExistent = tempDir.resolve("does-not-exist");
        registry.discoverSkills(nonExistent, Set.of());
        assertFalse(registry.hasSkills());
    }

    @Test
    void testDiscoverSkillsWithFilter() throws IOException {
        createTestSkill("skill-one", "First test skill");
        createTestSkill("skill-two", "Second test skill");

        // Only discover skill-one
        registry.discoverSkills(tempDir, Set.of("skill-one"));

        assertEquals(1, registry.getSkillNames().size());
        assertTrue(registry.getSkillNames().contains("skill-one"));
        assertFalse(registry.getSkillNames().contains("skill-two"));
    }

    @Test
    void testEmptyCatalog() {
        assertFalse(registry.hasSkills());
        final var catalog = registry.formatCatalog();
        assertTrue(catalog.contains("No skills available"));
    }

    @Test
    void testFormatCatalog() throws IOException {
        createTestSkill("skill-one", "First skill");
        createTestSkill("skill-two", "Second skill");
        registry.discoverSkills(tempDir, Set.of());

        final var catalog = registry.formatCatalog();

        assertTrue(catalog.contains("skill-one"));
        assertTrue(catalog.contains("skill-two"));
        assertTrue(catalog.contains("First skill"));
        assertTrue(catalog.contains("Second skill"));
    }

    @Test
    void testGetSkillCatalog() throws IOException {
        createTestSkill("my-skill", "My skill description");
        registry.discoverSkills(tempDir, Set.of());

        final var catalog = registry.getSkillCatalog();

        assertNotNull(catalog);
        assertEquals(1, catalog.size());
        assertTrue(catalog.containsKey("my-skill"));
        assertEquals("My skill description", catalog.get("my-skill"));
    }

    @Test
    void testLoadNonexistentSkill() throws IOException {
        createTestSkill("test-skill", "A test skill");
        registry.discoverSkills(tempDir, Set.of());

        final var skillOpt = registry.loadSkill("nonexistent");

        assertFalse(skillOpt.isPresent());
    }

    @Test
    void testLoadSkill() throws IOException {
        createTestSkill("test-skill", "A test skill");
        registry.discoverSkills(tempDir, Set.of());

        final var skillOpt = registry.loadSkill("test-skill");

        assertTrue(skillOpt.isPresent());
        assertEquals("test-skill", skillOpt.get().getName());
    }

    @Test
    void testLoadSkillCacheHit() throws IOException {
        createTestSkill("test-skill", "A test skill");
        registry.discoverSkills(tempDir, Set.of());

        // First load
        final var skillOpt1 = registry.loadSkill("test-skill");
        // Second load - should return cached version
        final var skillOpt2 = registry.loadSkill("test-skill");

        assertTrue(skillOpt1.isPresent());
        assertTrue(skillOpt2.isPresent());
        assertEquals(skillOpt1.get(), skillOpt2.get());
    }

    @Test
    void testLoadSkillFromPath() throws IOException {
        final var skillDir = tempDir.resolve("direct-skill");
        Files.createDirectory(skillDir);

        final var skillContent = """
                ---
                name: direct-skill
                description: Directly loaded skill
                ---

                # Instructions
                """;

        Files.writeString(skillDir.resolve("SKILL.md"), skillContent);

        final var skillOpt = registry.loadSkillFromPath(skillDir);

        assertTrue(skillOpt.isPresent());
        assertEquals("direct-skill", skillOpt.get().getName());
        assertTrue(registry.getSkillNames().contains("direct-skill"));
    }

    @Test
    void testLoadSkillFromPathNotDirectory() {
        assertThrows(IllegalArgumentException.class,
                     () -> registry.loadSkillFromPath(tempDir.resolve("nonexistent")));
    }

    private void createTestSkill(String name, String description) throws IOException {
        final var skillDir = tempDir.resolve(name);
        Files.createDirectory(skillDir);

        final var skillContent = """
                ---
                name: %s
                description: %s
                ---

                # %s Instructions
                """.formatted(name, description, name);

        Files.writeString(skillDir.resolve("SKILL.md"), skillContent);
    }
}
