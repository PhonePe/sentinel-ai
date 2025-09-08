package configuredagents.capabilities.impl;

import configuredagents.capabilities.AgentCapability;
import configuredagents.capabilities.AgentCapabilityVisitor;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.Set;

/**
 * Provides the capability for an agent to use locally registered tools.
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AgentCustomToolCapability extends AgentCapability {

    /**
     * Set of tools to be available to this agent
     */
    Set<String> selectedTools;

    @Builder
    @Jacksonized
    public AgentCustomToolCapability(@NonNull Set<String> selectedTools) {
        super(Type.CUSTOM_TOOLS);
        this.selectedTools = selectedTools;
    }

    @Override
    public <T> T accept(AgentCapabilityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
