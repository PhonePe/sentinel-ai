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

package com.phonepe.sentinelai.configuredagents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapability;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.With;

import java.util.List;
import java.util.Objects;

/**
 * Configuration for dynamically spun up {@link ConfiguredAgent}.
 */
@Value
@Builder
@AllArgsConstructor
@With
public class AgentConfiguration {
    /**
     * Name of the agent to be configured.
     */
    @NonNull
    String agentName;

    /**
     * Detailed description of the agent.
     */
    @NonNull
    String description;

    /**
     * System prompt to be used for the agent.
     */
    @NonNull
    String prompt;
    /**
     * Input schema for the agent. Will default to String if not provided.
     */
    JsonNode inputSchema;

    /**
     * Output schema for the agent. Will default to String if not provided.
     */
    JsonNode outputSchema;

    /**
     * Capabilities of the agent.
     */
    @Singular
    List<AgentCapability> capabilities;

    /**
     * Overriding model configuration for the agent.
     */
    ModelConfiguration modelConfiguration;

    /**
     * Fixes the configuration by setting default schemas and capabilities if they are not provided.
     * @param configuration Original configuration
     * @param mapper Object mapper to create schema etc. if needed
     * @return Fixed configuration with all fields filled up
     */
    public static AgentConfiguration fixConfiguration(@NonNull AgentConfiguration configuration,
                                                      final ObjectMapper mapper) {
        return new AgentConfiguration(
                configuration.getAgentName(),
                configuration.getDescription(),
                configuration.getPrompt(),
                Objects.requireNonNullElseGet(configuration.getInputSchema(),
                                              () -> JsonUtils.schemaForPrimitive(String.class, "data", mapper)),
                Objects.requireNonNullElseGet(configuration.getOutputSchema(),
                                              () -> JsonUtils.schema(String.class)),
                Objects.requireNonNullElseGet(configuration.getCapabilities(), List::of),
                configuration.getModelConfiguration());
    }
}
