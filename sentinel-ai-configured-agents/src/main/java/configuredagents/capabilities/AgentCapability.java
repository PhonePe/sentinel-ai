package configuredagents.capabilities;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import configuredagents.capabilities.impl.AgentCustomToolCapability;
import configuredagents.capabilities.impl.AgentMCPCapability;
import configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Expresses the capabilities of an agent.
 */
@EqualsAndHashCode
@ToString
@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = "REMOTE_HTTP_CALLS", value = AgentRemoteHttpCallCapability.class),
        @JsonSubTypes.Type(name = "MCP", value = AgentMCPCapability.class),
        @JsonSubTypes.Type(name = "CUSTOM_TOOLS", value = AgentCustomToolCapability.class),
})
public abstract class AgentCapability {
    public enum Type {
        REMOTE_HTTP_CALLS,
        MCP,
        CUSTOM_TOOLS,
        TOOL_INHERITANCE,
        AGENT_MEMORY,
        SESSION_MANAGEMENT,
    }
    private final Type type;

    protected AgentCapability(@NonNull Type type) {
        this.type = type;
    }

    public abstract <T> T accept(AgentCapabilityVisitor<T> visitor);
}
