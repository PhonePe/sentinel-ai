package com.phonepe.sentinelai.configuredagents;

import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.AgentCapabilityVisitor;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentCustomToolCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentMCPCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentMemoryCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentRemoteHttpCallCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.AgentSessionManagementCapability;
import com.phonepe.sentinelai.configuredagents.capabilities.impl.ParentToolInheritanceCapability;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.tools.ComposingToolBox;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.toolbox.mcp.MCPToolBox;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolBox;
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
    private final AgentSetupProvider agentSetupProvider;
    private final ModelFactory modelFactory;

    @Builder
    public ConfiguredAgentFactory(
            final HttpToolboxFactory httpToolboxFactory,
            final MCPToolBoxFactory mcpToolboxFactory,
            final CustomToolBox customToolBox,
            final AgentMessagesPreProcessors messagesPreProcessors,
            final AgentSetupProvider agentSetupProvider,
            final ModelFactory modelFactory) {
        this.httpToolboxFactory = null != httpToolboxFactory
                                  ? new SimpleCache<>(upstream -> httpToolboxFactory.create(upstream)
                .orElseThrow(() -> new IllegalArgumentException("No HTTP tool box found for upstream: " + upstream)))
                                  : null;
        this.mcpToolboxFactory = null != mcpToolboxFactory
                                 ? new SimpleCache<>(upstream -> mcpToolboxFactory.create(upstream)
                .orElseThrow(() -> new IllegalArgumentException("No MCP tool box found for upstream: " + upstream)))
                                 : null;
        this.customToolBox = customToolBox;
        this.agentSetupProvider = Objects.requireNonNullElseGet(agentSetupProvider, ConfigDrivenAgentSetupProvider::new);
        this.modelFactory = Objects.requireNonNullElseGet(modelFactory, SimpleOpenAIModelFactory::new);
    }

    public final ConfiguredAgent createAgent(@NonNull final AgentMetadata agentMetadata, Agent<?, ?, ?> parent) {
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
                                Objects.requireNonNullElseGet(remoteHttpCallCapability.getSelectedTools(),
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

                    @Override
                    public Void visit(ParentToolInheritanceCapability parentToolInheritanceCapability) {
                        final var parentTools = parent.tools();
                        final var exposedTools = Objects.requireNonNullElseGet(
                                parentToolInheritanceCapability.getSelectedTools(), Set::<String>of);
                        toolBoxes.add(
                                CustomToolBox.filter(agentConfiguration.getAgentName(),
                                                     "agent-%s-tools".formatted(parent.name()),
                                                     parentTools,
                                                     exposedTools));
                        return null;
                    }
                }));
        toolBoxes.addAll(extensions); //Because all extensions are also toolboxes

        final var agentSetup = agentSetupProvider.from(parent.getSetup(), agentConfiguration, modelFactory);

        return new ConfiguredAgent(
                agentConfiguration,
                extensions,
                new ComposingToolBox(toolBoxes, Set.of()),
                agentSetup);
    }

}
