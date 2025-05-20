package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.utils.Pair;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 *
 */
@Slf4j
public class MCPToolBox implements ToolBox {
    private final McpSyncClient mcpClient;
    private final ObjectMapper mapper;

    public MCPToolBox(McpSyncClient mcpClient, ObjectMapper mapper) {
        this.mcpClient = mcpClient;
        this.mapper = mapper;
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        return mcpClient.listTools()
                .tools()
                .stream()
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
                                                    .name(toolDef.name())
                                                    .description(Objects.requireNonNullElseGet(
                                                            toolDef.description(),
                                                            toolDef::name))
                                                    .contextAware(false)
                                                    .build(),
                                            mapper.valueToTree(params),
                                            this::runTool);

                })
                .collect(toMap(tool -> tool.getToolDefinition().getName(),
                               Function.identity()));
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
    public ExternalTool.ExternalToolResponse runTool(String toolName, String args) {
        log.debug("Calling MCP tool: {} with args: {}",
                  toolName, args);
        final var res = mcpClient.callTool(new McpSchema.CallToolRequest(toolName, args));
        return new ExternalTool.ExternalToolResponse(
                res.content(),
                Boolean.FALSE.equals(res.isError())
                ? ErrorType.TOOL_CALL_TEMPORARY_FAILURE
                : ErrorType.SUCCESS);

    }
}
