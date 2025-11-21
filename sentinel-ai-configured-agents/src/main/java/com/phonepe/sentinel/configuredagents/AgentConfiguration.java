package com.phonepe.sentinel.configuredagents;

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinel.configuredagents.capabilities.AgentCapability;
import lombok.*;

import java.util.List;

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

}
