package com.phonepe.sentinelai.configuredagents.capabilities;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentCustomToolCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentMCPCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.ParentToolInheritanceCapability;
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
        @JsonSubTypes.Type(name = "TOOL_INHERITANCE", value = ParentToolInheritanceCapability.class),
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
