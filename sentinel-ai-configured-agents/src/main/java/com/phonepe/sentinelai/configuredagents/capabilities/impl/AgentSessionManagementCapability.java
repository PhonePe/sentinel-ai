package com.phonepe.sentinelai.configuredagents.capabilities.impl;

import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapabilityVisitor;

/**
 * Enables session management capabilities for the agent.
 */
public class AgentSessionManagementCapability extends AgentCapability {
    public AgentSessionManagementCapability() {
        super(Type.SESSION_MANAGEMENT);
    }

    @Override
    public <T> T accept(AgentCapabilityVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
