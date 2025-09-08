package configuredagents.capabilities;

import configuredagents.capabilities.impl.*;

/**
 * To handle capability specific behavior in a type-safe manner,
 */
public interface AgentCapabilityVisitor<T> {
    T visit(AgentRemoteHttpCallCapability remoteHttpCallCapability);

    T visit(AgentMCPCapability mcpCapability);

    T visit(AgentCustomToolCapability customToolCapability);

    T visit(AgentMemoryCapability memoryCapability);

    T visit(AgentSessionManagementCapability sessionManagementCapability);

}
