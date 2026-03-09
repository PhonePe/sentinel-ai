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

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * Represents the YAML frontmatter metadata in a SKILL.md file
 */
@Value
@Builder
@Jacksonized
public class SkillMetadata {

    /**
     * Required: skill name (1-64 chars, lowercase, numbers, hyphens)
     */
    String name;

    /**
     * Required: description of what the skill does and when to use it (max 1024 chars)
     */
    String description;

    /**
     * Optional: license identifier or reference
     */
    @Nullable
    String license;

    /**
     * Optional: compatibility requirements (max 500 chars)
     */
    @Nullable
    String compatibility;

    /**
     * Optional: additional metadata key-value pairs
     */
    @Nullable
    Map<String, Object> metadata;

    /**
     * Optional: space-delimited list of pre-approved tools
     */
    @Nullable
    @JsonProperty("allowed-tools")
    String allowedTools;

    /**
     * Validate that name matches the parent directory name
     */
    public void validateName(String directoryName) {
        if (!name.equals(directoryName)) {
            throw new IllegalArgumentException(
                                               "Skill name '" + name + "' must match directory name '" + directoryName
                                                       + "'");
        }
    }
}
