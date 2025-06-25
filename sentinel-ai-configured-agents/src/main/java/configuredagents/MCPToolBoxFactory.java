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
 * A factory to create instances of {@link MCPToolBox} from the client provider. We do not apply tool filters here,
 * that is done in the {@link ConfiguredAgentFactory} where we can apply filters based on the agent configuration.
 */
@AllArgsConstructor
@Builder
public class MCPToolBoxFactory {
    @NonNull
    private final ObjectMapper objectMapper;

    @NonNull
    private final Function<String, Optional<McpSyncClient>> clientProvider;

    public Optional<MCPToolBox> create(String upstream) {
        return clientProvider.apply(upstream)
                .map(client-> new MCPToolBox(upstream, client, objectMapper, Set.of()));
    }
}
