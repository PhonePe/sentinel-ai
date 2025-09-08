package configuredagents;

import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.tools.ComposingToolBox;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.toolbox.mcp.MCPToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
import configuredagents.capabilities.AgentCapability;
import configuredagents.capabilities.AgentCapabilityVisitor;
import configuredagents.capabilities.impl.*;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Factory for creating instances of {@link ConfiguredAgent} based on the provided configuration
 */
@Slf4j
public class ConfiguredAgentFactory {
    private final SimpleCache<HttpToolBox> httpToolboxFactory;
    private final SimpleCache<MCPToolBox> mcpToolboxFactory;
    private final CustomToolBox customToolBox;

    @Builder
    public ConfiguredAgentFactory(
            final HttpToolboxFactory httpToolboxFactory,
            final MCPToolBoxFactory mcpToolboxFactory,
            final CustomToolBox customToolBox) {
        this.httpToolboxFactory = null != httpToolboxFactory
                                  ? new SimpleCache<>(upstream -> httpToolboxFactory.create(upstream)
                .orElseThrow(() -> new IllegalArgumentException("No HTTP tool box found for upstream: " + upstream)))
                                  : null;
        this.mcpToolboxFactory = null != mcpToolboxFactory
                                 ? new SimpleCache<>(upstream -> mcpToolboxFactory.create(upstream)
                .orElseThrow(() -> new IllegalArgumentException("No MCP tool box found for upstream: " + upstream)))
                                 : null;
        this.customToolBox = customToolBox;
    }

    public final ConfiguredAgent createAgent(@NonNull final AgentMetadata agentMetadata) {
        final var agentConfiguration = agentMetadata.getConfiguration();
        final var capabilities = Objects.requireNonNullElseGet(agentConfiguration.getCapabilities(),
                                                               List::<AgentCapability>of);
        final var toolBoxes = new ArrayList<ToolBox>();
        final var extensions = new ArrayList<AgentExtension<String, String, ConfiguredAgent.RootAgent>>();

        capabilities.forEach(
                agentCapability -> agentCapability.accept(new AgentCapabilityVisitor<Void>() {
                    @Override
                    public Void visit(AgentRemoteHttpCallCapability remoteHttpCallCapability) {
                        if (null == httpToolboxFactory) {
                            log.warn("HTTP Tool Box Factory is not configured. HTTP call capability will not be added");
                            return null;
                        }
                        final var selectedTools =
                                Objects.requireNonNullElseGet(remoteHttpCallCapability.getSelectedRemoteTools(),
                                                              Map::<String, Set<String>>of);
                        toolBoxes.addAll(
                                selectedTools
                                        .entrySet()
                                        .stream()
                                        .map(toolsFromUpstream -> new ComposingToolBox(
                                                List.of(httpToolboxFactory.find(toolsFromUpstream.getKey())
                                                                .orElseThrow(() -> new IllegalArgumentException(
                                                                        "No HTTP tool box found for: " + toolsFromUpstream.getKey()))),
                                                toolsFromUpstream.getValue()))
                                        .toList());
                        return null;
                    }

                    @Override
                    public Void visit(AgentMCPCapability mcpCapability) {
                        if (null == mcpToolboxFactory) {
                            log.warn("MCP Tool Box Factory is not configured. MCP call capability will not be added");
                            return null;
                        }
                        final var selectedTools = Objects.requireNonNullElseGet(mcpCapability.getSelectedTools(),
                                                                                Map::<String, Set<String>>of);
                        toolBoxes.addAll(
                                selectedTools
                                        .entrySet()
                                        .stream()
                                        .map(toolsFromUpstream -> new ComposingToolBox(
                                                List.of(mcpToolboxFactory.find(toolsFromUpstream.getKey())
                                                                .orElseThrow(() -> new IllegalArgumentException(
                                                                        "No MCP tool box found for: " + toolsFromUpstream.getKey()))),
                                                toolsFromUpstream.getValue()))
                                        .toList());
                        return null;
                    }

                    @Override
                    public Void visit(AgentCustomToolCapability customToolCapability) {
                        if (null == customToolBox) {
                            log.warn("custom tools have not been registered, but capability provided");
                            return null;
                        }
                        final var exposedTools = Objects.requireNonNullElseGet(customToolCapability.getSelectedTools(),
                                                                               Set::<String>of);
                        toolBoxes.add(
                                CustomToolBox.filter(agentConfiguration.getAgentName(),
                                                     customToolBox,
                                                     exposedTools));
                        return null;
                    }

                    @Override
                    public Void visit(AgentMemoryCapability memoryCapability) {
                        return null;
                    }

                    @Override
                    public Void visit(AgentSessionManagementCapability sessionManagementCapability) {
                        return null;
                    }
                }));
        toolBoxes.addAll(extensions); //Because all extensions are also toolboxes

        return new ConfiguredAgent(
                agentConfiguration.getAgentName(),
                agentConfiguration.getDescription(),
                agentConfiguration.getPrompt(),
                extensions,
                new ComposingToolBox(toolBoxes, Set.of()),
                agentConfiguration.getInputSchema(),
                agentConfiguration.getOutputSchema());
    }

}
