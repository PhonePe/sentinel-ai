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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.google.common.collect.Maps;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.FactList;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agent.SystemPrompt;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.ToolUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Extension that provides Agent Skills capabilities to Sentinel AI agents.
 *
 * <p>Skills are loaded from the filesystem using a two-tier mechanism:
 *
 * <ul>
 *   <li>Tier 1 (discovery): Only metadata is loaded when the extension is initialized.
 *   <li>Tier 2 (loading): Full instructions and assets are loaded on demand when a skill is
 *       activated.
 * </ul>
 *
 * <p>In multi-skill mode the extension injects a skills catalog into the agent's system prompt and
 * registers {@code listSkills}, {@code activateSkill}, and {@code readSkillReference} tools. In
 * single-skill mode the skill's instructions are injected directly into the system prompt and only
 * the {@code readSkillReference} tool is registered.
 *
 * @param <R> Request type
 * @param <T> Response type
 * @param <A> Agent type
 */
@Slf4j
public class AgentSkillsExtension<R, T, A extends Agent<R, T, A>>
        implements AgentExtension<R, T, A> {

    private static final String READ_SKILL_REFERENCE_TOOL_ID =
            "agent_skills_extension_read_skill_reference";
    private final SkillRegistry registry = new SkillRegistry();

    private final boolean singleSkillMode;

    private final Map<String, ExecutableTool> tools;

    /**
     * Create an extension in multi-skill mode. Skills are discovered (metadata only) from all
     * provided directories.
     *
     * @param baseDir Base directory used to resolve relative paths
     * @param skillsDirectories List of directory paths (absolute or relative) to scan for skills
     * @param skillsToLoad Optional filter; when non-empty only the named skills are discovered
     */
    @SneakyThrows
    @Builder(builderMethodName = "withMultipleSkills", builderClassName = "MultiSkillBuilder")
    public AgentSkillsExtension(
            @NonNull String baseDir,
            @NonNull List<String> skillsDirectories,
            Collection<String> skillsToLoad) {
        // Discover skills from all provided directories
        final var skillsFilter =
                Set.copyOf(Objects.requireNonNullElseGet(skillsToLoad, Set::<String>of));
        log.info("Discovering skills from the following directories: {}", skillsDirectories);
        for (String dirPath : skillsDirectories) {
            final var skillsDir = expandSkillDir(dirPath, baseDir);
            registry.discoverSkills(skillsDir, skillsFilter);
        }
        this.singleSkillMode = false;
        this.tools = readTools(this);
        displaySkillsCatalog();
    }

    private void displaySkillsCatalog() {
        if (log.isInfoEnabled()) {
            log.info("Skills catalog:\n{}", registry.formatCatalog());
        }
    }

    /**
     * Create an extension in single-skill mode. The skill is loaded immediately from the given
     * path.
     *
     * @param baseDir Base directory used to resolve relative paths
     * @param singleSkill Path (absolute or relative to {@code baseDir} / cwd) to the skill
     *     directory
     */
    @SneakyThrows
    @Builder(builderMethodName = "withSingleSkill", builderClassName = "SingleSkillBuilder")
    public AgentSkillsExtension(@NonNull String baseDir, final String singleSkill) {
        var skillPath = expandSkillDir(singleSkill, baseDir);
        this.registry.loadSkillFromPath(skillPath);
        this.singleSkillMode = true;
        this.tools = readTools(this);
        displaySkillsCatalog();
    }

    private static StringBuilder addSection(
            StringBuilder sb, String title, Collection<String> items) {
        if (items.isEmpty()) {
            return sb;
        }
        sb.append("\n\n## ").append(title).append("\n");
        items.forEach(item -> sb.append("- ").append(item).append("\n"));
        return sb;
    }

    /**
     * Tries to resolve a skill directory path.
     *
     * <ol>
     *   <li>Uses the path as-is if it is already an existing directory.
     *   <li>Resolves relative to {@code baseDir}.
     *   <li>Resolves relative to the current working directory.
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

    private static Map<String, ExecutableTool> readTools(AgentSkillsExtension<?, ?, ?> extension) {
        final var allTools = ToolUtils.readTools(extension);
        // In single skill mode, we won't have the list skills and activate skill tools -
        // the instructions will be injected directly via system prompts
        if (extension.singleSkillMode) {
            return Map.copyOf(
                    Maps.filterKeys(allTools, key -> key.equals(READ_SKILL_REFERENCE_TOOL_ID)));
        }
        return Map.copyOf(allTools);
    }

    private static String skillNotFoundError(final String skillName) {
        return "Error: Skill '" + skillName + "' not found in catalog.";
    }

    @Tool("Activate a skill by name to access its instructions and capabilities")
    public String activateSkill(@JsonPropertyDescription("Name of the skill to activate") String skillName) {

        log.info("Activating skill: {}", skillName);

        final var skill = registry.loadSkill(skillName).orElse(null);
        if (null == skill) {
            return skillNotFoundError(skillName);
        }

        final var response = new StringBuilder();
        response.append("# Skill Activated: ").append(skill.getName()).append("\n\n");
        response.append(skill.getInstructions());

        addSection(
                response,
                "Available Reference Files",
                Objects.requireNonNullElseGet(skill.getReferenceFiles(), Map::<String, Path>of)
                        .keySet());
        addSection(
                response,
                "Available Scripts",
                Objects.requireNonNullElseGet(skill.getScriptFiles(), Map::<String, Path>of)
                        .keySet());
        addSection(
                response,
                "Available Assets",
                Objects.requireNonNullElseGet(skill.getAssetFiles(), Map::<String, Path>of)
                        .keySet());

        log.info("Skill '{}' activated successfully", skillName);
        return response.toString();
    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(
            R request, AgentRunContext<R> metadata, A agent, ProcessingMode processingMode) {

        final var tasks = new ArrayList<SystemPrompt.Task>();
        final var hints = new ArrayList<>();

        // In single-skill mode, don't add skill discovery - just inject the skill directly
        if (!singleSkillMode) {
            if (registry.hasSkills()) {
                // Add task for skill discovery and activation
                tasks.add(
                        SystemPrompt.Task.builder()
                                .objective(
                                        "Check if any available skills are relevant to the user's request")
                                .instructions(
                                        """
                                Before proceeding with the main task:
                                1. Review the available skills catalog below
                                2. If a skill seems relevant to the user's request, use the activate_skill tool
                                3. Once activated, follow the skill's instructions carefully
                                4. You can activate multiple skills if needed
                                5. Always prefer activating relevant skills over using general tools, as skills may provide specialized capabilities and context
                                """)
                                .tool(
                                        tools.values().stream()
                                                .map(
                                                        tool ->
                                                                SystemPrompt.ToolSummary.builder()
                                                                        .name(
                                                                                tool.getToolDefinition()
                                                                                        .getId())
                                                                        .description(
                                                                                tool.getToolDefinition()
                                                                                        .getDescription())
                                                                        .build())
                                                .toList())
                                .build());
                hints.add(registry.formatCatalog());
            }
        } else {
            // This assumes that registry::loadSkill has already been called
            final var skillName = registry.getSkillNames().stream().findFirst().orElse(null);
            if (skillName == null) {
                log.warn("Single skill mode enabled but no skills found in registry");
                return new ExtensionPromptSchema(tasks, hints);
            }
            tasks.add(
                    SystemPrompt.Task.builder()
                            .objective(
                                    "Use the provided instructions to assist with the user's request")
                            .instructions(activateSkill(skillName))
                            .build());
        }
        return new ExtensionPromptSchema(tasks, hints);
    }

    @Override
    public List<FactList> facts(R request, AgentRunContext<R> context, A agent) {
        // No dynamic facts needed for skills - the instructions are provided via system prompts
        // and the tools are always available
        return List.of();
    }

    @Tool("List all available skills with their descriptions")
    public String listSkills() {
        if (!registry.hasSkills()) {
            return "No skills are currently available.";
        }

        return registry.formatCatalog();
    }

    @Override
    public String name() {
        return "agent-skills";
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        return Optional.empty();
    }

    @Tool("Read a reference file from an activated skill")
    public String readSkillReference(
            @JsonPropertyDescription("Name of the skill") String skillName,
            @JsonPropertyDescription(
                            "Path to the reference file within the skill's references/ directory")
                    String referenceFile) {

        final var skill = registry.getLoadedSkill(skillName).orElse(null);
        if (null == skill) {
            return skillNotFoundError(skillName);
        }

        if (skill.getReferenceFiles() == null || skill.getReferenceFiles().isEmpty()) {
            return "Error: Skill '" + skillName + "' has no reference files.";
        }

        if (!skill.getReferenceFiles().containsKey(referenceFile)) {
            return "Error: Reference file '"
                    + referenceFile
                    + "' not found in skill '"
                    + skillName
                    + "'.";
        }

        try {
            return Files.readString(skill.getReferenceFiles().get(referenceFile));
        } catch (Exception e) {
            log.error("Failed to read reference file {}: {}", referenceFile, e.getMessage());
            return "Error reading reference file: " + e.getMessage();
        }
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        return tools;
    }
}
