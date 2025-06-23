package configuredagents;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.toolbox.mcp.MCPToolBox;
import io.modelcontextprotocol.client.McpSyncClient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 *
 */
@AllArgsConstructor
@Builder
public class MCPToolBoxFactory {
    @NonNull
    private final ObjectMapper objectMapper;

    @NonNull
    private final Function<String, McpSyncClient> clientProvider;

    public Optional<MCPToolBox> create(String upstream) {
        final var client = clientProvider.apply(upstream);

        return Optional.of(new MCPToolBox(upstream, client, objectMapper, Set.of()));
    }
}
