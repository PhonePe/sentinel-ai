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

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

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
    private final Set<String> exposedTools;
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
        this.exposedTools = exposedTools;
    }

    public Map<String, ExecutableTool> tools() {
        if (!knownTools.isEmpty()) {
            return knownTools;
        }
        log.debug("Loading tools from MCP server: {}", name);
        //The read happens independently and uses putAll to load values into the map in a threadsafe manner
        knownTools.putAll(mcpClient.listTools()
                                  .tools()
                                  .stream()
                                  .filter(toolDef -> exposedTools.isEmpty() || exposedTools.contains(toolDef.name()))
                                  .map(toolDef -> {
                                      final var toolParams = toolDef.inputSchema();
                                      // The following looks redundant, but it is not
                                      // The MCP library does not populate all param names as required, openai expects
                                      // all to be present
                                      final var params = mapper.createObjectNode();
                                      params.put("type", "object");
                                      params.put("additionalProperties", false);
                                      params.set("properties",
                                                 mapper.valueToTree(toolParams.properties()
                                                                            .entrySet()
                                                                            .stream()
                                                                            .map(this::convertParameter)
                                                                            .collect(toMap(Pair::getFirst, Pair::getSecond))
                                                                   ));
                                      params.set("required",
                                                 mapper.valueToTree(toolParams.properties().keySet()));
                                      return new ExternalTool(ToolDefinition.builder()
                                                                      .id(AgentUtils.id(name, toolDef.name()))
                                                                      .name(toolDef.name())
                                                                      .description(Objects.requireNonNullElseGet(
                                                                              toolDef.description(),
                                                                              toolDef::name))
                                                                      .contextAware(false)
                                                                      .build(),
                                                              mapper.valueToTree(params),
                                                              this::runTool);

                                  })
                                  .collect(toMap(tool -> tool.getToolDefinition().getId(),
                                                 Function.identity())));
        log.info("Loaded {} tools from MCP server: {}", knownTools.size(), name);
        return knownTools;
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
