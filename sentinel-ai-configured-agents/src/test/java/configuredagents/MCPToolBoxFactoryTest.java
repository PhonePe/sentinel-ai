package configuredagents;

import com.phonepe.sentinelai.core.utils.JsonUtils;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests {@link MCPToolBoxFactory}.
 */
class MCPToolBoxFactoryTest {

    @Test
    void test() {
        try(final var mcpClient = mock(McpSyncClient.class)) {
            final var factory = MCPToolBoxFactory.builder()
                    .objectMapper(JsonUtils.createMapper())
                    .clientProvider(upstream -> {
                        if ("testUpstream".equals(upstream)) {
                            return Optional.of(mcpClient);
                        }
                        return Optional.empty();
                    })
                    .build();

            assertTrue(factory.create("testUpstream").isPresent());
            assertFalse(factory.create("invalidUpstream").isPresent());
        }
    }
}