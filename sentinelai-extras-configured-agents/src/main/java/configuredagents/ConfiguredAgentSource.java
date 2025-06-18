package configuredagents;

import lombok.Value;

import java.util.List;
import java.util.Optional;

/**
 *
 */
public interface ConfiguredAgentSource {
    @Value
    class AgentSearchResponse {
        String agentId;
        AgentConfiguration configuration;
    }

    Optional<AgentMetadata> save(final String agentId, final AgentConfiguration agentConfiguration);

    Optional<AgentMetadata> read(String agentId);
    List<AgentMetadata> list();
    List<AgentSearchResponse> find(final String query);
    boolean remove(String agentId);
}
