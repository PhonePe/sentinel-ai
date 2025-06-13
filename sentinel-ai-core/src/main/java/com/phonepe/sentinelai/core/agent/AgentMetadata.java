package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

/**
 * Metadata for an agent, which includes its name, description, input schema, and output schema.
 */
@Value
public class AgentMetadata {
    /**
     * Name of the agent
     */
    String name;

    /**
     * Description of the agent
     */
    String description;

    JsonNode inputSchema;
    JsonNode outputSchema;
}
