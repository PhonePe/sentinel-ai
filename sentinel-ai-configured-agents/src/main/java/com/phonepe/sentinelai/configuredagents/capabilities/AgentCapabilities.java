package com.phonepe.sentinelai.configuredagents.capabilities;

import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentCustomToolCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentMCPCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.ParentToolInheritanceCapability;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Set;

/**
 * Easy way to create capabilities for agents.
 */
@UtilityClass
public class AgentCapabilities {
    public static AgentCapability remoteHttpCalls(Map<String, Set<String>> selectedUpstreams) {
        return new AgentRemoteHttpCallCapability(selectedUpstreams);
    }

    public static AgentCapability mcpCalls(Map<String, Set<String>> selectedUpstreams) {
        return new AgentMCPCapability(selectedUpstreams);
    }

    public static AgentCapability customToolCalls(Set<String> selectedTools) {
        return new AgentCustomToolCapability(selectedTools);
    }

    public static AgentCapability inheritToolsFromParent(Set<String> selectedTools) {
        return new ParentToolInheritanceCapability(selectedTools);
    }
}
