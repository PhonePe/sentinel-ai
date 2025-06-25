package configuredagents;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent configuration source that stores agent configurations in memory.
 */
public class InMemoryAgentConfigurationSource implements AgentConfigurationSource {

    private final Map<String, AgentMetadata> agentConfigurations = new ConcurrentHashMap<>();

    @Override
    public Optional<AgentMetadata> save(String agentId, AgentConfiguration agentConfiguration) {
        return Optional.of(agentConfigurations.computeIfAbsent(
                agentId,
                id -> new AgentMetadata(agentId, agentConfiguration)));
    }

    @Override
    public Optional<AgentMetadata> read(String agentId) {
        return Optional.ofNullable(agentConfigurations.get(agentId));
    }

    @Override
    public List<AgentMetadata> list() {
        return List.copyOf(agentConfigurations.values());
    }

    @Override
    public List<AgentSearchResponse> find(String query) {
        throw new UnsupportedOperationException("Find operation is not supported in InMemoryAgentConfigurationSource");
    }

    @Override
    public boolean remove(String agentId) {
        return agentConfigurations.remove(agentId) != null;
    }


}
