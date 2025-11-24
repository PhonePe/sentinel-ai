package com.phonepe.sentinelai.configuredagents.capabilities;

import com.phonepe.sentinel.configuredagents.capabilities.impl.*;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.*;

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
