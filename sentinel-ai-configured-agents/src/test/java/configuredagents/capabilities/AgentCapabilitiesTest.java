package configuredagents.capabilities;

import configuredagents.capabilities.impl.AgentMCPCapability;
import configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AgentCapabilities}
 */
class AgentCapabilitiesTest {

    @Test
    void test() {
        final var remoteHttpCalls = AgentCapabilities.remoteHttpCalls(Map.of("upstream1", Set.of("http://example.com")));
        assertNotNull(remoteHttpCalls);
        assertInstanceOf(AgentRemoteHttpCallCapability.class, remoteHttpCalls);
        assertThrowsExactly(NullPointerException.class, () -> AgentCapabilities.remoteHttpCalls(null));
        final var mcpCalls = AgentCapabilities.mcpCalls(Map.of("upstream2", Set.of("mcp://example.com")));
        assertNotNull(mcpCalls);
        assertInstanceOf(AgentMCPCapability.class, mcpCalls);
        assertThrowsExactly(NullPointerException.class, () -> AgentCapabilities.mcpCalls(null));
    }

}