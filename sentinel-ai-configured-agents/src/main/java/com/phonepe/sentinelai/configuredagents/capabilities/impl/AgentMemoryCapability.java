package com.phonepe.sentinelai.configuredagents.capabilities.impl;

import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapabilityVisitor;

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
