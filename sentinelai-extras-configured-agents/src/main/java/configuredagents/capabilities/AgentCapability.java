package configuredagents.capabilities;

import lombok.Data;
import lombok.NonNull;

/**
 * Expresses the capabilities of an agent.
 */
@Data
public abstract class AgentCapability {
    public enum Type {
        REMOTE_HTTP_CALLS,
        MCP,
        AGENT_MEMORY,
        SESSION_MANAGEMENT,
    }
    private final Type type;

    protected AgentCapability(@NonNull Type type) {
        this.type = type;
    }

    public abstract <T> T accept(AgentCapabilityVisitor<T> visitor);
}
