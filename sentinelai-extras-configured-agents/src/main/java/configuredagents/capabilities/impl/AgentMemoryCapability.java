package configuredagents.capabilities.impl;

import configuredagents.capabilities.AgentCapability;
import configuredagents.capabilities.AgentCapabilityVisitor;

/**
 *
 */
public class AgentMemoryCapability extends AgentCapability {

    public AgentMemoryCapability() {
        super(Type.AGENT_MEMORY);
    }

    @Override
    public <T> T accept(AgentCapabilityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
