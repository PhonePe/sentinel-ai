/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.toolbox.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericResource;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.NonContextualDefaultExternalToolRunner;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPHttpServerConfig;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPSSEServerConfig;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPServerConfig;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPServerConfigVisitor;
import com.phonepe.sentinelai.toolbox.mcp.config.MCPStdioServerConfig;

import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 * An internal client for MCP server that exposes tools as {@link ExecutableTool}s.
 */
@Slf4j
public class SentinelMCPClient implements AutoCloseable {
    private static final String SAMPLING_OUTPUT_KEY = "mcpSamplingOutput";

    @Getter
    private final String name;
    private final McpSyncClient mcpClient;
    private final ObjectMapper mapper;
    private final JacksonMcpJsonMapper jacksonMapper;
    private final Set<String> exposedTools = new CopyOnWriteArraySet<>();
    private final Map<String, ExecutableTool> knownTools = new ConcurrentHashMap<>();

    private Agent<?, ?, ?> agent;

    public SentinelMCPClient(@NonNull final String name,
                             @NonNull final MCPServerConfig mcpServerConfig,
                             @NonNull final ObjectMapper mapper,
                             @Singular final Set<String> exposedTools) {
        this.name = name;
        this.mapper = mapper;
        this.jacksonMapper = new JacksonMcpJsonMapper(mapper);
        this.mcpClient = createMcpClient(mcpServerConfig);
        this.exposeTools(exposedTools);
    }

    public SentinelMCPClient(@NonNull String name,
                             @NonNull McpSyncClient mcpClient,
                             @NonNull ObjectMapper mapper,
                             @Singular Set<String> exposedTools) {
        this.name = name;
        this.mcpClient = mcpClient;
        this.mapper = mapper;
        this.jacksonMapper = new JacksonMcpJsonMapper(mapper);
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
        exposedTools.addAll(Objects.requireNonNullElseGet(toolIds,
                                                          ArrayList::new));
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
            knownTools.putAll(toolsList(mcpClient.listTools().tools()));
            log.info("Loaded {} tools from MCP server {}: {}",
                     knownTools.size(),
                     name,
                     knownTools.keySet());
        }
        final var mapToReturn = exposedTools.isEmpty() ? knownTools : knownTools
                .entrySet()
                .stream()
                .filter(entry -> exposedTools.contains(entry.getValue()
                        .getToolDefinition()
                        .getName()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        return Map.copyOf(mapToReturn);
    }

    @SneakyThrows
    public ExternalTool.ExternalToolResponse runTool(AgentRunContext<?> context,
                                                     String toolId,
                                                     String args) {
        final var tool = knownTools.get(toolId);
        if (null == tool) {
            return new ExternalTool.ExternalToolResponse("Invalid tool: %s"
                    .formatted(toolId), ErrorType.TOOL_CALL_PERMANENT_FAILURE);
        }
        log.debug("Calling MCP tool: {} with args: {}", toolId, args);
        try {
            final var res = mcpClient.callTool(new McpSchema.CallToolRequest(
                                                                             jacksonMapper,
                                                                             tool.getToolDefinition()
                                                                                     .getName(),
                                                                             args));
            return new ExternalTool.ExternalToolResponse(res.content(),
                                                         Boolean.TRUE.equals(res
                                                                 .isError())
                                                                         ? ErrorType.TOOL_CALL_TEMPORARY_FAILURE
                                                                         : ErrorType.SUCCESS);
        }
        catch (Exception e) {
            final var message = AgentUtils.rootCause(e).getMessage();
            log.error("Error calling MCP tool {}: {}", toolId, message);
            return new ExternalTool.ExternalToolResponse("Error processing request: " + message,
                                                         ErrorType.GENERIC_MODEL_CALL_FAILURE);
        }

    }

    private McpSyncClient createMcpClient(MCPServerConfig serverConfig) {
        final var transport = serverConfig.accept(
                                                  new MCPServerConfigVisitor<McpClientTransport>() {
                                                      @Override
                                                      public McpClientTransport visit(MCPHttpServerConfig httpServerConfig) {
                                                          final int timeout = Objects
                                                                  .requireNonNullElse(httpServerConfig
                                                                          .getTimeout(),
                                                                                      5_000);
                                                          final var providedHeaders = Objects
                                                                  .requireNonNullElseGet(httpServerConfig
                                                                          .getHeaders(),
                                                                                         Map::<String, String>of);
                                                          return HttpClientStreamableHttpTransport
                                                                  .builder(httpServerConfig
                                                                          .getUrl())
                                                                  .connectTimeout(Duration
                                                                          .ofMillis(timeout))
                                                                  .jsonMapper(jacksonMapper)
                                                                  .customizeRequest(requestBuilder -> {
                                                                      requestBuilder
                                                                              .timeout(Duration
                                                                                      .ofMillis(timeout));
                                                                      providedHeaders
                                                                              .forEach(requestBuilder::header);
                                                                  })
                                                                  .build();
                                                      }

                                                      @Override
                                                      public McpClientTransport visit(MCPSSEServerConfig sseServerConfig) {
                                                          final int timeout = Objects
                                                                  .requireNonNullElse(sseServerConfig
                                                                          .getTimeout(),
                                                                                      5_000);
                                                          return HttpClientSseClientTransport
                                                                  .builder(sseServerConfig
                                                                          .getUrl())
                                                                  .jsonMapper(jacksonMapper)
                                                                  .connectTimeout(Duration
                                                                          .ofMillis(timeout))
                                                                  .customizeRequest(requestBuilder -> requestBuilder
                                                                          .timeout(Duration
                                                                                  .ofMillis(timeout)))
                                                                  .build();
                                                      }

                                                      @Override
                                                      public McpClientTransport visit(MCPStdioServerConfig stdioServerConfig) {
                                                          final var serverParameters = ServerParameters
                                                                  .builder(stdioServerConfig
                                                                          .getCommand())
                                                                  .args(Objects
                                                                          .requireNonNullElseGet(stdioServerConfig
                                                                                  .getArgs(),
                                                                                                 List::of))
                                                                  .env(Objects
                                                                          .requireNonNullElseGet(stdioServerConfig
                                                                                  .getEnv(),
                                                                                                 Map::of))
                                                                  .build();
                                                          return new StdioClientTransport(serverParameters,
                                                                                          jacksonMapper);
                                                      }
                                                  });

        final var client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("sentinel-ai-toolbox-mcp",
                                                         "X.X.X"))
                .sampling(this::handleSamplingRequest)
                .toolsChangeConsumer(tools -> {
                    knownTools.clear();
                    log.info("Received tools change notification from MCP server: {}. " + "Will reload on next tools call",
                             name);
                })
                .build();
        final var result = client.initialize();
        log.debug("Initialized MCP client for server: {} with result: {}",
                  name,
                  result);
        return client;
    }

    private Map<String, ExternalTool> toolsList(final List<McpSchema.Tool> tools) {
        return tools.stream()
                .map(toolDef -> new ExternalTool(ToolDefinition.builder()
                        .id(AgentUtils.id(name, toolDef.name()))
                        .name(toolDef.name())
                        .description(Objects.requireNonNullElseGet(toolDef
                                .description(), toolDef::name))
                        .contextAware(false)
                        .strictSchema(false)
                        // IMPORTANT::Strict means openai expects all object params to be
                        // present in the required field. This is not something all MCP
                        // servers do properly. So for now we are setting strict false
                        // for tools obtained from mcp servers
                        .terminal(false)
                        .retries(ToolDefinition.NO_RETRY) //Let the model retry if needed
                        .timeoutSeconds(ToolDefinition.NO_TIMEOUT) //Can be maintained at client level
                        .build(),
                                                 mapper.valueToTree(toolDef
                                                         .inputSchema()),
                                                 this::runTool))
                .collect(toMap(tool -> tool.getToolDefinition().getId(),
                               Function.identity()));
    }

    private McpSchema.CreateMessageResult handleSamplingRequest(McpSchema.CreateMessageRequest createMessageRequest) {
        if (null == agent) {
            return new McpSchema.CreateMessageResult(McpSchema.Role.ASSISTANT,
                                                     new McpSchema.TextContent("Sampling call failed. No agent is registered to handle the request"),
                                                     "NoAgent",
                                                     McpSchema.CreateMessageResult.StopReason.END_TURN);
        }
        log.debug("Handling sampling request: {}", createMessageRequest);
        final var agentSetup = agent.getSetup();
        final var setup = agentSetup.getModelSettings()
                .withMaxTokens(createMessageRequest.maxTokens())
                .withTemperature(Objects.requireNonNullElse(createMessageRequest
                        .temperature(), 0.0f).floatValue());
        final var messages = new ArrayList<AgentMessage>();
        final var runId = "sampling-" + UUID.randomUUID();
        messages.add(new SystemPrompt(null,
                                      runId,
                                      createMessageRequest.systemPrompt(),
                                      true,
                                      null));
        messages.addAll(convertFromSamplingToAgentMessages(null,
                                                           runId,
                                                           createMessageRequest
                                                                   .messages()));
        final var modelRunContext = new ModelRunContext(agent.name(),
                                                        runId,
                                                        null,
                                                        null,
                                                        agentSetup
                                                                .withModelSettings(setup),
                                                        new ModelUsageStats(),
                                                        ProcessingMode.DIRECT);
        try {
            final var response = agentSetup.getModel()
                    .compute(modelRunContext,
                             List.of(new ModelOutputDefinition(SAMPLING_OUTPUT_KEY,
                                                               "Response to sampling calls",
                                                               JsonUtils.schema(
                                                                                String.class))),
                             messages,
                             Map.of(),
                             new NonContextualDefaultExternalToolRunner(null,
                                                                        runId,
                                                                        mapper),
                             new NeverTerminateEarlyStrategy(),
                             List.of())
                    .join();

            final var responseNode = response.getData()
                    .get(SAMPLING_OUTPUT_KEY);
            if (JsonUtils.empty(responseNode)) {
                return new McpSchema.CreateMessageResult(McpSchema.Role.ASSISTANT,
                                                         new McpSchema.TextContent("Sampling call failed. No content was generated"),
                                                         agentSetup.getModel()
                                                                 .getClass()
                                                                 .getSimpleName(),
                                                         McpSchema.CreateMessageResult.StopReason.END_TURN);
            }
            return new McpSchema.CreateMessageResult(McpSchema.Role.ASSISTANT,
                                                     new McpSchema.TextContent(responseNode
                                                             .asText()),
                                                     agentSetup.getModel()
                                                             .getClass()
                                                             .getSimpleName(),
                                                     toStopReason(response
                                                             .getError()
                                                             .getErrorType()));
        }
        catch (Exception e) {
            final var message = AgentUtils.rootCause(e).getMessage();
            if (log.isDebugEnabled()) {
                log.error("Error running sampling call: ", e);
            }
            return new McpSchema.CreateMessageResult(McpSchema.Role.ASSISTANT,
                                                     new McpSchema.TextContent("Error processing request: " + message),
                                                     agentSetup.getModel()
                                                             .getClass()
                                                             .getSimpleName(),
                                                     toStopReason(ErrorType.GENERIC_MODEL_CALL_FAILURE));
        }
    }

    private McpSchema.CreateMessageResult.StopReason toStopReason(ErrorType errorType) {
        return switch (errorType) {
            case LENGTH_EXCEEDED ->
                McpSchema.CreateMessageResult.StopReason.MAX_TOKENS;
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

    private List<AgentMessage> convertFromSamplingToAgentMessages(String sessionId,
                                                                  String runId,
                                                                  List<McpSchema.SamplingMessage> messages) {
        return messages.stream()
                .map(message -> switch (message.content().type()) {
                    case "text" -> new GenericText(sessionId,
                                                   runId,
                                                   translateRole(message
                                                           .role()),
                                                   ((McpSchema.TextContent) message
                                                           .content()).text());
                    case "resource" -> convertResourceResponse(sessionId,
                                                               runId,
                                                               message);
                    default -> throw new IllegalArgumentException(
                                                                  "Unsupported type");
                })
                .toList();
    }

    @SneakyThrows
    private AgentMessage convertResourceResponse(String sessionId,
                                                 String runId,
                                                 McpSchema.SamplingMessage message) {
        final var embeddedResource = (McpSchema.EmbeddedResource) message
                .content();
        return switch (embeddedResource.type()) {
            case "text" -> new GenericResource(sessionId,
                                               runId,
                                               translateRole(message.role()),
                                               GenericResource.ResourceType.TEXT,
                                               embeddedResource.resource()
                                                       .uri(),
                                               embeddedResource.resource()
                                                       .mimeType(),
                                               ((McpSchema.TextResourceContents) embeddedResource
                                                       .resource()).text(),
                                               mapper.writeValueAsString(embeddedResource
                                                       .resource()));
            case "blob" -> new GenericResource(sessionId,
                                               runId,
                                               translateRole(message.role()),
                                               GenericResource.ResourceType.BLOB,
                                               embeddedResource.resource()
                                                       .uri(),
                                               embeddedResource.resource()
                                                       .mimeType(),
                                               ((McpSchema.BlobResourceContents) embeddedResource
                                                       .resource()).blob(),
                                               mapper.writeValueAsString(embeddedResource
                                                       .resource()));
            default -> throw new IllegalArgumentException(
                                                          "Unsupported resource type: " + embeddedResource
                                                                  .type());

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
