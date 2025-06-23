package configuredagents.capabilities.impl;

import configuredagents.capabilities.AgentCapability;
import configuredagents.capabilities.AgentCapabilityVisitor;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Map;
import java.util.Set;

/**
 * Provides the capability for an agent to use MCP servers.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AgentMCPCapability extends AgentCapability {

    /**
     * A map of upstream names for MCP servers to the set of tools that can be used with them.
     * Set can be left empty to be able to use all tools exposed by the MCP server.
     */
    Map<String, Set<String>> selectedTools;

    @Builder
    @Jacksonized
    public AgentMCPCapability(@NonNull Map<String, Set<String>> selectedTools) {
        super(Type.MCP);
        this.selectedTools = selectedTools;
    }

    @Override
    public <T> T accept(AgentCapabilityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
