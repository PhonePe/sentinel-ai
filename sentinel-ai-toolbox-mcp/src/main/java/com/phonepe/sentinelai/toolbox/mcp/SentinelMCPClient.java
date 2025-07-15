package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericResource;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPServerConfig;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
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

    private Agent<?, ?, ?> agent;

    public SentinelMCPClient(
            @NonNull final String name,
            @NonNull final MCPServerConfig mcpServerConfig,
            @NonNull final ObjectMapper mapper,
            @Singular final Set<String> exposedTools) {
        this.name = name;
        this.mapper = mapper;
        final var clientData = MCPJsonReader.createMcpClient(mapper,
                                                             name,
                                                             mcpServerConfig,
                                                             this::handleSamplingRequest);

        this.mcpClient = clientData.getClient();
        this.exposeTools(exposedTools);
    }

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

    public <R, T, A extends Agent<R, T, A>> void onRegistrationCompleted(A agent) {
        this.agent = agent;
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

    @SneakyThrows
    public ExternalTool.ExternalToolResponse runTool(AgentRunContext<?> context, String toolId, String args) {
        final var tool = knownTools.get(toolId);
        if (null == tool) {
            return new ExternalTool.ExternalToolResponse("Invalid tool: %s".formatted(toolId),
                                                         ErrorType.TOOL_CALL_PERMANENT_FAILURE);
        }
        log.debug("Calling MCP tool: {} with args: {}", toolId, args);
        try {
            final var res = mcpClient.callTool(new McpSchema.CallToolRequest(tool.getToolDefinition().getName(), args));
            return new ExternalTool.ExternalToolResponse(
                    res.content(),
                    Boolean.FALSE.equals(res.isError())
                    ? ErrorType.TOOL_CALL_TEMPORARY_FAILURE
                    : ErrorType.SUCCESS);
        }
        catch (Exception e) {
            final var message = AgentUtils.rootCause(e).getMessage();
            log.error("Error calling MCP tool {}: {}", toolId, message);
            return new ExternalTool.ExternalToolResponse(
                    "Error processing request: " + message,
                    ErrorType.GENERIC_MODEL_CALL_FAILURE);
        }

    }

    private McpSchema.CreateMessageResult handleSamplingRequest(McpSchema.CreateMessageRequest createMessageRequest) {
        log.debug("Handling sampling request: {}", createMessageRequest);
        final var agentSetup = agent.getSetup();
        final var setup = agentSetup.getModelSettings()
                .withMaxTokens(createMessageRequest.maxTokens())
                .withTemperature(Objects.requireNonNullElse(createMessageRequest.temperature(), 0.1f).floatValue());
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new SystemPrompt(createMessageRequest.systemPrompt(), true, null));
        messages.addAll(convertFromSamplingToAgentMessages(createMessageRequest.messages()));
        try {
            final var response = agentSetup.getModel()
                    .runDirect(setup,
                               Objects.requireNonNullElseGet(agentSetup.getExecutorService(),
                                                             Executors::newCachedThreadPool),
                               createMessageRequest.systemPrompt(),
                               null,
                               messages)
                    .join();

            return new McpSchema.CreateMessageResult(
                    McpSchema.Role.ASSISTANT,
                    new McpSchema.TextContent(response.getData().asText()),
                    agentSetup.getModel().getClass().getSimpleName(),
                    toStopReason(response.getError().getErrorType()));
        }
        catch (Exception e) {
            final var message = AgentUtils.rootCause(e).getMessage();
            return new McpSchema.CreateMessageResult(
                    McpSchema.Role.ASSISTANT,
                    new McpSchema.TextContent("Error processing request: " + message),
                    agentSetup.getModel().getClass().getSimpleName(),
                    toStopReason(ErrorType.GENERIC_MODEL_CALL_FAILURE));
        }
    }

    private McpSchema.CreateMessageResult.StopReason toStopReason(ErrorType errorType) {
        return switch (errorType) {
            case LENGTH_EXCEEDED -> McpSchema.CreateMessageResult.StopReason.MAX_TOKENS;
            default -> McpSchema.CreateMessageResult.StopReason.END_TURN;
/*
            case SUCCESS, NO_RESPONSE -> null;
            case REFUSED -> null;
            case FILTERED -> null;
            case TOOL_CALL_PERMANENT_FAILURE -> null;
            case TOOL_CALL_TEMPORARY_FAILURE -> null;
            case JSON_ERROR -> null;
            case SERIALIZATION_ERROR -> null;
            case DESERIALIZATION_ERROR -> null;
            case UNKNOWN_FINISH_REASON -> null;
            case UNKNOWN -> null;
*/
        };
    }

    private List<AgentMessage> convertFromSamplingToAgentMessages(
            List<McpSchema.SamplingMessage> messages) {
        return messages
                .stream()
                .map(message -> switch (message.content().type()) {
                    case "text" -> new GenericText(translateRole(message.role()),
                                                   ((McpSchema.TextContent) message.content()).text());
                    case "resource" -> convertResourceResponse(message);
                    default -> throw new IllegalArgumentException("Unsupported type");
                })
                .toList();
    }

    @SneakyThrows
    private AgentMessage convertResourceResponse(McpSchema.SamplingMessage message) {
        final var embeddedResource = (McpSchema.EmbeddedResource) message.content();
        return switch (embeddedResource.type()) {
            case "text" -> new GenericResource(translateRole(message.role()),
                                               GenericResource.ResourceType.TEXT,
                                               embeddedResource.resource().uri(),
                                               embeddedResource.resource().mimeType(),
                                               ((McpSchema.TextResourceContents) embeddedResource.resource()).text(),
                                               mapper.writeValueAsString(embeddedResource.resource()));
            case "blob" -> new GenericResource(translateRole(message.role()),
                                               GenericResource.ResourceType.TEXT,
                                               embeddedResource.resource().uri(),
                                               embeddedResource.resource().mimeType(),
                                               ((McpSchema.BlobResourceContents) embeddedResource.resource()).blob(),
                                               mapper.writeValueAsString(embeddedResource.resource()));
            default -> throw new IllegalArgumentException("Unsupported resource type: " + embeddedResource.type());

        };
    }

    private AgentGenericMessage.Role translateRole(McpSchema.Role role) {
        return switch (role) {
            case USER -> AgentGenericMessage.Role.USER;
            case ASSISTANT -> AgentGenericMessage.Role.ASSISTANT;
        };
    }

    @Override
    public void close() {
        mcpClient.close();
    }
}
