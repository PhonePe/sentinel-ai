package configuredagents;

import com.phonepe.sentinelai.core.utils.AgentUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link InMemoryAgentConfigurationSource}.
 */
class InMemoryAgentConfigurationSourceTest {

    @Test
    void testSaveAndRead() {
        final var source = new InMemoryAgentConfigurationSource();
        final var config = new AgentConfiguration(
                "Test Agent",
                "Agent for testing",
                "You are a test agent.",
                null,
                null,
                List.of());
        String agentId = AgentUtils.id(config.getAgentName());

        // Save the agent configuration
        final var savedMetadata = source.save(agentId, config);
        assertTrue(savedMetadata.isPresent());
        assertEquals(agentId, savedMetadata.get().getId());
        assertEquals(config, savedMetadata.get().getConfiguration());

        // Read the agent configuration
        var readMetadata = source.read(agentId);
        assertTrue(readMetadata.isPresent());
        assertEquals(agentId, readMetadata.get().getId());
        assertEquals(config, readMetadata.get().getConfiguration());

        //Delete configuration
        assertTrue(source.remove(agentId));
        assertFalse(source.read(agentId).isPresent());
        assertFalse(source.remove("invalid-id"));

        //Assert that find does not work
        assertThrows(UnsupportedOperationException.class, () -> source.find("Test"));
    }
}