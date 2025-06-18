package configuredagents;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.*;

import java.util.Map;
import java.util.Set;

/**
 *
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
    @NonNull
    JsonNode outputSchema;

    /**
     * Remote tools to be used by the agent.
     */
    Map<String, Set<String>> selectedRemoteHttpTools;

    /**
     * MCP tools to be used by the agent.
     */
    Map<String, Set<String>> selectedMCPTools;

    boolean memoryEnabled;
}
