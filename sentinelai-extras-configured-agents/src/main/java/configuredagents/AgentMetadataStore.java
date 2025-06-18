package configuredagents;

import java.util.List;
import java.util.Optional;

/**
 *
 */
public interface AgentMetadataStore {
    void save(final AgentMetadata agentMetadata);
    Optional<AgentMetadata> read(final String agentName);
    List<AgentMetadata> findAgents(final String query);
    List<AgentMetadata> allAgents();
}
