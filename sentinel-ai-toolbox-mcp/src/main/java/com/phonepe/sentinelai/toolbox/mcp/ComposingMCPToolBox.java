package com.phonepe.sentinelai.toolbox.mcp;

import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;

import static java.util.stream.Collectors.toUnmodifiableMap;

/**
 * ComposingMCPToolBox is a tool box that composes multiple MCP clients.
 * It allows for dynamic selection of tools from different MCP clients.
 */
@Slf4j
public class ComposingMCPToolBox implements ToolBox {
    private final Map<String, SentinelMCPClient> mcpClients = new ConcurrentHashMap<>();
    private final Set<String> selectedTools = new CopyOnWriteArraySet<>();

    @Builder
    public ComposingMCPToolBox(
            @Singular final Set<String> selectedTools,
            @Singular final Collection<SentinelMCPClient> mcpClients) {
        this.selectedTools.addAll(Objects.requireNonNullElseGet(selectedTools, Set::of));
        this.mcpClients.putAll(Objects.requireNonNullElseGet(mcpClients, List::<SentinelMCPClient>of)
                                       .stream()
                                       .collect(toUnmodifiableMap(SentinelMCPClient::getName, Function.identity())));
    }

    public ComposingMCPToolBox registerMCPClient(@NonNull SentinelMCPClient client) {
        mcpClients.put(client.getName(), client);
        return this;
    }

    public ComposingMCPToolBox registerSelectedTool(String toolName) {
        if (!Strings.isNullOrEmpty(toolName)) {
            selectedTools.add(toolName);
        }
        return this;
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        log.debug("Composing tools from MCP clients: {}", mcpClients.keySet());
        final var relevantTools = Map.copyOf(mcpClients.values()
                                                     .stream()
                                                     .flatMap(client -> client.tools()
                                                             .entrySet()
                                                             .stream())
                                                     .filter(tool -> selectedTools.isEmpty() || selectedTools.contains(tool.getKey()))
                                                     .collect(toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue)));
        log.debug("Found {} tools in ComposingMCPToolBox: {}", relevantTools.size(), relevantTools.keySet());
        return relevantTools;
    }
}
