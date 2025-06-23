package configuredagents;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.NonNull;
import lombok.Value;

/**
 * Metadata for an agent exposed to the LLM.
 */
@Value
public class ExposedAgentMetadata {
    /**
     * Name of the agent
     */
    String id;

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
     * Input schema for the agent. Will default to String if not provided.
     */
    JsonNode inputSchema;

    /**
     * Output schema for the agent. Will default to String if not provided.
     */
    @NonNull
    JsonNode outputSchema;
}
