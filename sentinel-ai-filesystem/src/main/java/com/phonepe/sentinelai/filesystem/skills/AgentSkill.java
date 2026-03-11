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

import lombok.Builder;
import lombok.Value;

import java.nio.file.Path;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * Represents a complete Agent Skill loaded from a directory
 */
@Value
@Builder
public class AgentSkill {

    /**
     * Skill metadata from YAML frontmatter
     */
    SkillMetadata metadata;

    /**
     * Markdown instructions body (after frontmatter)
     */
    String instructions;

    /**
     * Path to the skill directory
     */
    Path skillDirectory;

    /**
     * Optional: Paths to reference files in references/ subdirectory
     */
    @Nullable
    Map<String, Path> referenceFiles;

    /**
     * Optional: Paths to script files in scripts/ subdirectory
     */
    @Nullable
    Map<String, Path> scriptFiles;

    /**
     * Optional: Paths to asset files in assets/ subdirectory
     */
    @Nullable
    Map<String, Path> assetFiles;

    /**
     * Format skill for display in catalog (name + description only)
     */
    public String formatCatalogEntry() {
        return String.format("- **%s**: %s", getName(), getDescription());
    }

    /**
     * Get the skill description from metadata
     */
    public String getDescription() {
        return metadata.getDescription();
    }

    /**
     * Get the skill name from metadata
     */
    public String getName() {
        return metadata.getName();
    }
}
