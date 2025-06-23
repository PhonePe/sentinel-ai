package configuredagents.capabilities;

import configuredagents.capabilities.impl.AgentMCPCapability;
import configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
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
}
