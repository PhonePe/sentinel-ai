package configuredagents;

import lombok.Value;

/**
 * Metadata for an agent, which includes its name, description, input schema, and output schema.
 */
@Value
public class AgentMetadata {
    /**
     * Name of the agent
     */
    String id;

    /**
     * Configuration for the agent
     */
    AgentConfiguration configuration;
}
