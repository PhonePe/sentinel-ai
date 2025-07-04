package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.Pair;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

/**
 * An internal client for MCP server that exposes tools as {@link ExecutableTool}s.
 */
@Slf4j
public class SentinelMCPClient implements AutoCloseable {
    @Getter
    private final String name;
    private final McpSyncClient mcpClient;
    private final ObjectMapper mapper;
    private final Set<String> exposedTools = new CopyOnWriteArraySet<>();
    private final Map<String, ExecutableTool> knownTools = new ConcurrentHashMap<>();

    @Builder
    public SentinelMCPClient(
            @NonNull String name,
            @NonNull McpSyncClient mcpClient,
            @NonNull ObjectMapper mapper,
            @Singular Set<String> exposedTools) {
        this.name = name;
        this.mcpClient = mcpClient;
        this.mapper = mapper;
        this.exposeTools(exposedTools);
    }

    public SentinelMCPClient exposeTools(String... toolId) {
        exposedTools.addAll(Arrays.asList(toolId));
        return this;
    }

    public SentinelMCPClient exposeTools(Collection<String> toolIds) {
        exposedTools.addAll(Objects.requireNonNullElseGet(toolIds, ArrayList::new));
        return this;
    }

    public SentinelMCPClient exposeAllTools() {
        exposedTools.clear();
        return this;
    }

    public Map<String, ExecutableTool> tools() {
        if (knownTools.isEmpty()) {
            log.debug("Loading tools from MCP server: {}", name);
            //The read happens independently and uses putAll to load values into the map in a threadsafe manner
            knownTools.putAll(mcpClient.listTools()
                                      .tools()
                                      .stream()
                                      .map(toolDef -> new ExternalTool(
                                              ToolDefinition.builder()
                                                      .id(AgentUtils.id(name, toolDef.name()))
                                                      .name(toolDef.name())
                                                      .description(
                                                              Objects.requireNonNullElseGet(
                                                                      toolDef.description(),
                                                                      toolDef::name))
                                                      .contextAware(false)
                                                      .strictSchema(false)
                                                      // IMPORTANT::Strict means openai expects all object params to be
                                                      // present in the required field. This is not something all MCP
                                                      // servers do properly. So for now we are setting strict false
                                                      // for tools obtained from mcp servers
                                                      .build(),
                                              mapper.valueToTree(toolDef.inputSchema()),
                                              this::runTool))
                                      .collect(toMap(tool -> tool.getToolDefinition().getId(),
                                                     Function.identity())));
            log.info("Loaded {} tools from MCP server {}: {}", knownTools.size(), name, knownTools.keySet());
        }
        final var mapToReturn = exposedTools.isEmpty()
                                ? knownTools
                                : knownTools.entrySet()
                                        .stream()
                                        .filter(entry -> exposedTools.contains(entry.getValue()
                                                                                       .getToolDefinition()
                                                                                       .getName()))
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return Map.copyOf(mapToReturn);
    }

    @NotNull
    @SuppressWarnings("unchecked")
    private Pair<String, ObjectNode> convertParameter(Map.Entry<String, Object> e) {
        final var paramNode = mapper.createObjectNode();

        //This will fail if mcp lib moves to some other type and needs to be fixed then
        final var param = (Map<String, String>) e.getValue();
        paramNode.put("type", param.get("type"));
        paramNode.put("description", param.get("description"));
        return Pair.of(e.getKey(), paramNode);
    }

    @SneakyThrows
    public ExternalTool.ExternalToolResponse runTool(String toolId, String args) {
        final var tool = knownTools.get(toolId);
        if (null == tool) {
            return new ExternalTool.ExternalToolResponse("Invalid tool: %s".formatted(toolId),
                                                         ErrorType.TOOL_CALL_PERMANENT_FAILURE);
        }
        log.debug("Calling MCP tool: {} with args: {}", toolId, args);
        final var res = mcpClient.callTool(new McpSchema.CallToolRequest(tool.getToolDefinition().getName(), args));
        return new ExternalTool.ExternalToolResponse(
                res.content(),
                Boolean.FALSE.equals(res.isError())
                ? ErrorType.TOOL_CALL_TEMPORARY_FAILURE
                : ErrorType.SUCCESS);

    }

    @Override
    public void close() {
        mcpClient.close();
    }
}
