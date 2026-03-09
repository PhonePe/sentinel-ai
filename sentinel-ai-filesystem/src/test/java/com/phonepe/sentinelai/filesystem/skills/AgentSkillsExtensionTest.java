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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSkillsExtensionTest {

    @TempDir
    Path tempDir;

    private Path skillsDir;

    @BeforeEach
    void setUp() throws IOException {
        skillsDir = tempDir.resolve("skills");
        Files.createDirectory(skillsDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (Files.exists(tempDir)) {
            Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                }
                catch (IOException e) {
                    // Ignore
                }
            });
        }
    }

    @Test
    void testActivateSkill() throws IOException {
        createTestSkillWithReferenceFile("my-skill", "A skill", "guide.md", "# Guide Content");

        final var extension = AgentSkillsExtension.withMultipleSkills()
                .baseDir(tempDir.toString())
                .skillsDirectories(List.of(skillsDir.toString()))
                .skillsToLoad(null)
                .build();

        final var result = extension.activateSkill("my-skill");

        assertTrue(result.contains("my-skill"));
        assertTrue(result.contains("These are the instructions"));
        assertTrue(result.contains("guide.md"));
    }

    @Test
    void testActivateSkillNotFound() throws IOException {
        createTestSkill("my-skill", "A skill");

        final var extension = AgentSkillsExtension.withMultipleSkills()
                .baseDir(tempDir.toString())
                .skillsDirectories(List.of(skillsDir.toString()))
                .skillsToLoad(null)
                .build();

        final var result = extension.activateSkill("nonexistent-skill");

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("nonexistent-skill"));
    }

    @Test
    void testListSkillsWhenEmpty() {
        final var extension = AgentSkillsExtension.withMultipleSkills()
                .baseDir(tempDir.toString())
                .skillsDirectories(List.of(skillsDir.toString()))
                .skillsToLoad(null)
                .build();

        final var result = extension.listSkills();

        assertTrue(result.contains("No skills are currently available"));
    }

    @Test
    void testListSkillsWithSkills() throws IOException {
        createTestSkill("my-skill", "A great skill for testing");

        final var extension = AgentSkillsExtension.withMultipleSkills()
                .baseDir(tempDir.toString())
                .skillsDirectories(List.of(skillsDir.toString()))
                .skillsToLoad(null)
                .build();

        final var result = extension.listSkills();

        assertTrue(result.contains("my-skill"));
        assertTrue(result.contains("A great skill for testing"));
    }

    @Test
    void testMultipleSkillsToolsAvailable() throws IOException {
        createTestSkill("skill-one", "First skill");

        final var extension = AgentSkillsExtension.withMultipleSkills()
                .baseDir(tempDir.toString())
                .skillsDirectories(List.of(skillsDir.toString()))
                .skillsToLoad(null)
                .build();

        final var tools = extension.tools();
        assertNotNull(tools);
        assertTrue(tools.containsKey("agent_skills_extension_list_skills"));
        assertTrue(tools.containsKey("agent_skills_extension_activate_skill"));
        assertTrue(tools.containsKey("agent_skills_extension_read_skill_reference"));
    }

    @Test
    void testReadSkillReference() throws IOException {
        createTestSkillWithReferenceFile("my-skill", "A skill", "guide.md", "# Guide Content");

        final var extension = AgentSkillsExtension.withMultipleSkills()
                .baseDir(tempDir.toString())
                .skillsDirectories(List.of(skillsDir.toString()))
                .skillsToLoad(null)
                .build();

        // Activate first
        extension.activateSkill("my-skill");

        // Then read the reference
        final var result = extension.readSkillReference("my-skill", "guide.md");

        assertTrue(result.contains("# Guide Content"));
    }

    @Test
    void testReadSkillReferenceSkillNotLoaded() throws IOException {
        createTestSkill("my-skill", "A skill");

        final var extension = AgentSkillsExtension.withMultipleSkills()
                .baseDir(tempDir.toString())
                .skillsDirectories(List.of(skillsDir.toString()))
                .skillsToLoad(null)
                .build();

        // Try to read reference without activating first
        final var result = extension.readSkillReference("my-skill", "guide.md");

        assertTrue(result.contains("Error"));
    }

    @Test
    void testSingleSkillMode() throws IOException {
        final var skillDir = createTestSkill("single-skill", "A single skill");

        final var extension = AgentSkillsExtension.withSingleSkill()
                .baseDir(tempDir.toString())
                .singleSkill(skillDir.toString())
                .build();

        // In single skill mode only readSkillReference tool should be exposed
        final var tools = extension.tools();
        assertNotNull(tools);
        assertFalse(tools.containsKey("agent_skills_extension_list_skills"));
        assertFalse(tools.containsKey("agent_skills_extension_activate_skill"));
        assertTrue(tools.containsKey("agent_skills_extension_read_skill_reference"));
    }

    private Path createTestSkill(String name, String description) throws IOException {
        final var skillDir = skillsDir.resolve(name);
        Files.createDirectory(skillDir);

        final var skillContent = """
                ---
                name: %s
                description: %s
                ---

                # %s Instructions

                These are the instructions.
                """.formatted(name, description, name);

        Files.writeString(skillDir.resolve("SKILL.md"), skillContent);
        return skillDir;
    }

    private void createTestSkillWithReferenceFile(String name,
                                                  String description,
                                                  String refFileName,
                                                  String refContent) throws IOException {
        final var skillDir = createTestSkill(name, description);
        final var referencesDir = skillDir.resolve("references");
        Files.createDirectory(referencesDir);
        Files.writeString(referencesDir.resolve(refFileName), refContent);
    }
}
