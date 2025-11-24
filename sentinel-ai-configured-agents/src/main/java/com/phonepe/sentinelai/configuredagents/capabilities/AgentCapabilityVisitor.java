package com.phonepe.sentinelai.configuredagents.capabilities;

import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentCustomToolCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentMCPCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentMemoryCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentSessionManagementCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.ParentToolInheritanceCapability;

/**
 * To handle capability specific behavior in a type-safe manner,
 */
public interface AgentCapabilityVisitor<T> {
    T visit(AgentRemoteHttpCallCapability remoteHttpCallCapability);

    T visit(AgentMCPCapability mcpCapability);

    T visit(AgentCustomToolCapability customToolCapability);

    T visit(AgentMemoryCapability memoryCapability);

    T visit(AgentSessionManagementCapability sessionManagementCapability);

    T visit(ParentToolInheritanceCapability parentToolInheritanceCapability);
}
