package configuredagents.capabilities;

import configuredagents.capabilities.impl.AgentMCPCapability;
import configuredagents.capabilities.impl.AgentMemoryCapability;
import configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
import configuredagents.capabilities.impl.AgentSessionManagementCapability;

/**
 * To handle capability specific behavior in a type-safe manner,
 */
public interface AgentCapabilityVisitor<T> {
    T visit(AgentRemoteHttpCallCapability remoteHttpCallCapability);

    T visit(AgentMCPCapability mcpCapability);

    T visit(AgentMemoryCapability memoryCapability);

    T visit(AgentSessionManagementCapability sessionManagementCapability);
}
