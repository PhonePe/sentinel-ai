package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.*;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.CallableTool;
import com.phonepe.sentinelai.core.utils.Pair;
import io.github.sashirestela.openai.common.ResponseFormat;
import io.github.sashirestela.openai.common.function.FunctionCall;
import io.github.sashirestela.openai.common.tool.Tool;
import io.github.sashirestela.openai.common.tool.ToolType;
import io.github.sashirestela.openai.domain.chat.ChatMessage;
import io.github.sashirestela.openai.domain.chat.ChatRequest;
import io.github.sashirestela.openai.service.ChatCompletionServices;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.phonepe.sentinelai.core.model.OpenAIModel.raiseMessageReceivedEvent;
import static com.phonepe.sentinelai.core.model.OpenAIModel.raiseMessageSentEvent;
import static com.phonepe.sentinelai.core.utils.JsonUtils.schema;

/**
 * Model implementation based on SimpleOpenAI client.
 * <p>
 * Please check <a href="https://github.com/sashirestela/simple-openai">Simple OpenAI Repo</a>
 * for details of client usage
 */
@Value
@Slf4j
public class SimpleOpenAIModel<M extends ChatCompletionServices> implements Model {
    private static final String OUTPUT_VARIABLE_NAME = "output";

    String modelName;
    M openAIProvider;
    ObjectMapper mapper;

    @Override
    public <R, D, T, A extends Agent<R, D, T, A>> CompletableFuture<AgentOutput<T>> exchange_messages(
            AgentRunContext<D, R> context,
            Class<T> responseType,
            Map<String, CallableTool> tools,
            Agent.ToolRunner toolRunner,
            List<AgentExtension> extensions,
            A agent) {
        final var oldMessages = context.getOldMessages();
        final var modelSettings = context.getAgentSetup().getModelSettings();
        final var openAiMessages = new ArrayList<>(convertToOpenAIMessages(oldMessages));
        final var allMessages = new ArrayList<>(oldMessages);
        final var newMessages = new ArrayList<AgentMessage>();
        final var stats = new ModelUsageStats();
        return CompletableFuture.supplyAsync(() -> {
            AgentOutput<T> output = null;
            do {
                final var builder = ChatRequest.builder()
                        .messages(openAiMessages)
                        .model(modelName)
                        .n(1);
                applyModelSettings(modelSettings, builder);
                if (!tools.isEmpty()) {
                    builder.tools(tools.values()
                                          .stream()
                                          .map(tool -> {
                                              final var toolDefinition = tool.getToolDefinition();
                                              final var params = mapper.createObjectNode();
                                              params.put("type", "object");
                                              params.put("additionalProperties", false);
                                              params.set("properties",
                                                         mapper.valueToTree(toolDefinition.getParameters()));
                                              params.set("required",
                                                         mapper.valueToTree(toolDefinition.getParameters().keySet()));
                                              return new Tool(
                                                      ToolType.FUNCTION,
                                                      new Tool.ToolFunctionDef(toolDefinition.getName(),
                                                                               toolDefinition.getDescription(),
                                                                               params,
                                                                               true));
                                          })
                                          .toList());
                }
                builder.responseFormat(ResponseFormat.jsonSchema(structuredOutputSchema(responseType, extensions)));
                raiseMessageSentEvent(context, agent, oldMessages);
                final var stopwatch = Stopwatch.createStarted();

                final var completionResponse = openAIProvider.chatCompletions()
                        .create(builder.build())
                        .join();
                if (null != completionResponse.getUsage()) {
                    //TODO UPDATE CONTEXT USAGE
                }
                final var response = completionResponse
                        .getChoices()
                        .stream()
                        .findFirst()
                        .orElse(null);
                if (null == response) {
                    return AgentOutput.error(oldMessages, stats, SentinelError.error(ErrorType.NO_RESPONSE));
                }
                final var message = response.getMessage();
                output = switch (response.getFinishReason()) {
                    case "stop" -> {
                        final var refusal = message.getRefusal();
                        if (!Strings.isNullOrEmpty(refusal)) {
                            yield AgentOutput.error(oldMessages,
                                                    stats,
                                                    SentinelError.error(ErrorType.REFUSED, refusal));
                        }
                        final var content = message.getContent();
                        if (!Strings.isNullOrEmpty(content)) {
                            final var newMessage = new StructuredOutput(content);
                            allMessages.add(newMessage);
                            newMessages.add(newMessage);
                            raiseMessageReceivedEvent(context, agent, newMessage, stopwatch);
                            try {
                                yield AgentOutput.success(convertToResponse(responseType, content, extensions, agent),
                                                          newMessages,
                                                          allMessages,
                                                          stats);
                            }
                            catch (JsonProcessingException e) {
                                yield AgentOutput.error(oldMessages,
                                                        stats,
                                                        SentinelError.error(ErrorType.JSON_ERROR, e));
                            }
                        }
                        final var toolCalls = Objects.requireNonNullElseGet(message.getToolCalls(), List::<io.github.sashirestela.openai.common.tool.ToolCall>of);
                        if (!toolCalls.isEmpty()) {
                            yield handleToolCalls(context,
                                                  tools,
                                                  oldMessages,
                                                  context.getAgentSetup().getExecutorService(),
                                                  toolRunner,
                                                  toolCalls,
                                                  openAiMessages,
                                                  allMessages,
                                                  newMessages,
                                                  stats,
                                                  agent,
                                                  stopwatch);
                        }
                        yield AgentOutput.error(oldMessages, stats, SentinelError.error(ErrorType.NO_RESPONSE));
                    }
                    case "function_call", "tool_calls" -> {
                        final var toolCalls = Objects.requireNonNullElseGet(message.getToolCalls(), List::<io.github.sashirestela.openai.common.tool.ToolCall>of);

                        if (!toolCalls.isEmpty()) {
                            final AgentOutput<T> toolCallResponse = handleToolCalls(context,
                                                                                    tools,
                                                                                    oldMessages,
                                                                                    context.getAgentSetup().getExecutorService(),
                                                                                    toolRunner,
                                                                                    toolCalls,
                                                                                    openAiMessages,
                                                                                    allMessages,
                                                                                    newMessages,
                                                                                    stats,
                                                                                    agent,
                                                                                    stopwatch);
                            if (toolCallResponse != null) {
                                yield toolCallResponse;
                            }
                        }
                        yield null;
                    }
                    case "length" -> AgentOutput.error(oldMessages,
                                                     stats,
                                                     SentinelError.error(ErrorType.LENGTH_EXCEEDED));
                    case "content_filter" -> AgentOutput.error(oldMessages,
                                                             stats,
                                                             SentinelError.error(ErrorType.FILTERED));
                    default -> AgentOutput.error(oldMessages, stats, SentinelError.error(ErrorType.UNKNOWN));
                };
            } while (output == null || (output.getData() == null && output.getError() == null));
            return output;
        }, context.getAgentSetup().getExecutorService());
    }

    private static <R, T, D, A extends Agent<R,D,T,A>> AgentOutput<T> handleToolCalls(
            AgentRunContext<D, R> context,
            Map<String, CallableTool> tools,
            List<AgentMessage> oldMessages,
            ExecutorService executorService,
            Agent.ToolRunner toolRunner,
            List<io.github.sashirestela.openai.common.tool.ToolCall> toolCalls,
            ArrayList<ChatMessage> openAiMessages,
            ArrayList<AgentMessage> allMessages,
            ArrayList<AgentMessage> newMessages,
            ModelUsageStats stats,
            A agent,
            Stopwatch stopwatch) {
        final var jobs = toolCalls.stream()
                .map(toolCall -> CompletableFuture.supplyAsync(
                        () -> {
                            final var toolCallMessage = new ToolCall(toolCall.getId(),
                                                                     toolCall.getFunction().getName(),
                                                                     toolCall.getFunction().getArguments());
                            final var toolCallResponse = toolRunner.runTool(context,
                                                                            tools,
                                                                            toolCallMessage);
                            return Pair.of(toolCallMessage, toolCallResponse);
                        }, executorService))
                .toList();
        final var failedCalls = jobs.stream()
                .map(CompletableFuture::join)
                .map(pair -> {
                    final var toolCallMessage = pair.getFirst();
                    final var toolCallResponse = pair.getSecond();
                    openAiMessages.add(convertIndividualMessageToOpenIDFormat(toolCallMessage));
                    openAiMessages.add(convertIndividualMessageToOpenIDFormat(toolCallResponse));
                    allMessages.add(toolCallMessage);
                    newMessages.add(toolCallMessage);
                    raiseMessageReceivedEvent(context, agent, toolCallMessage, stopwatch);
                    allMessages.add(toolCallResponse);
                    newMessages.add(toolCallResponse);
                    return toolCallResponse;
                })
                .filter(Predicates.not(ToolCallResponse::isSuccess))
                .toList();

        if (!failedCalls.isEmpty()) {
            //TODO::DETERMINE IF CALLS HAVE FAILED PERMANENTLY
            return AgentOutput.error(oldMessages,
                                     stats,
                                     SentinelError.error(ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                         failedCalls.stream()
                                                                 .map(ToolCallResponse::getToolName)
                                                                 .toList()));
        }
        return null;
    }

    private <R, D, T, A extends Agent<R, D, T, A>>  T convertToResponse(
            Class<T> responseType, String content,
            List<AgentExtension> extensions, A agent) throws JsonProcessingException {
        final var outputNode = mapper.readTree(content);
        extensions.forEach(extension -> {
            final var outputDef = extension.outputSchema().orElse(null);
            if (null == outputDef) {
                log.debug("No output required for extension: {}", extension.name());
                return;
            }
            final var dataKey = outputDef.getKey();
            if (!outputNode.has(dataKey)) {
                log.error("Model output does not have required key '{}' for extension: {}", dataKey, extension.name());
                return;
            }
            try {
                extension.consume(outputNode.get(dataKey), agent);
            }
            catch (Exception e) {
                log.error("Extension %s failed to consume output.".formatted(extension.name()), e);
            }
        });
        return mapper.treeToValue(outputNode.get(OUTPUT_VARIABLE_NAME), responseType);
    }

    private ResponseFormat.JsonSchema structuredOutputSchema(final Class<?> clazz, List<AgentExtension> extensions) {
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        final var fields = mapper.createArrayNode();
        schema.set("required", fields);
        final var propertiesNode = mapper.createObjectNode();
        schema.set("properties", propertiesNode);

        fields.add(OUTPUT_VARIABLE_NAME);
        propertiesNode.set(OUTPUT_VARIABLE_NAME, schema(clazz));
        extensions.forEach(extension -> {
            final var outputDefinition = extension.outputSchema().orElse(null);
            if (null == outputDefinition) {
                log.debug("No output expected by extension: {}", extension.name());
                return;
            }
            fields.add(outputDefinition.getKey());
            propertiesNode.set(outputDefinition.getKey(), outputDefinition.getSchema());
        });
        return ResponseFormat.JsonSchema.builder()
                .name("model_output")
                .schema(schema)
                .strict(true)
                .build();
    }

    private static void applyModelSettings(ModelSettings modelSettings, ChatRequest.ChatRequestBuilder builder) {
        if (null == modelSettings) {
            log.debug("No model settings provided");
            return;
        }
        if (modelSettings.getMaxTokens() != null) {
            builder.maxTokens(modelSettings.getMaxTokens());
        }
        if (modelSettings.getTemperature() != null) {
            builder.temperature(Double.valueOf(modelSettings.getTemperature()));
        }
        if (modelSettings.getTopP() != null) {
            builder.topP(Double.valueOf(modelSettings.getTopP()));
        }
        if (modelSettings.getParallelToolCalls() != null) {
//            builder.parallelToolCalls(Boolean.TRUE.equals(modelSettings.getParallelToolCalls()));
            log.warn("Parallel tool calls not supported by SimpleOpenAI");
        }
        if (modelSettings.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(Double.valueOf(modelSettings.getFrequencyPenalty()));
        }
        if (modelSettings.getPresencePenalty() != null) {
            builder.presencePenalty(Double.valueOf(modelSettings.getPresencePenalty()));
        }
        if (modelSettings.getLogitBias() != null) {
            builder.logitBias(modelSettings.getLogitBias());
        }
    }

    private List<ChatMessage> convertToOpenAIMessages(List<AgentMessage> oldMessages) {
        return Objects.requireNonNullElseGet(oldMessages, List::<AgentMessage>of)
                .stream()
                .map(SimpleOpenAIModel::convertIndividualMessageToOpenIDFormat)
                .toList();

    }

    private static ChatMessage convertIndividualMessageToOpenIDFormat(AgentMessage agentMessage) {
        return agentMessage.accept(new AgentMessageVisitor<ChatMessage>() {
            @Override
            public ChatMessage visit(AgentRequest request) {
                return request.accept(new AgentRequestVisitor<ChatMessage>() {
                    @Override
                    public ChatMessage visit(SystemPrompt systemPrompt) {
                        return ChatMessage.SystemMessage.of(systemPrompt.getContent());
                    }

                    @Override
                    public ChatMessage visit(UserPrompt userPrompt) {
                        return ChatMessage.UserMessage.of(userPrompt.getContent());
                    }

                    @Override
                    public ChatMessage visit(ToolCallResponse toolCallResponse) {
                        return ChatMessage.ToolMessage.of(toolCallResponse.getResponse(),
                                                          toolCallResponse.getToolCallId());
                    }
                });
            }

            @Override
            public ChatMessage visit(AgentResponse response) {
                return response.accept(new AgentResponseVisitor<>() {
                    @Override
                    public ChatMessage visit(Text text) {
                        return ChatMessage.AssistantMessage.of(text.getContent());
                    }

                    @Override
                    public ChatMessage visit(StructuredOutput structuredOutput) {
                        return ChatMessage.AssistantMessage.of(structuredOutput.getContent());
                    }

                    @Override
                    public ChatMessage visit(ToolCall toolCall) {
                        return ChatMessage.AssistantMessage.of(List.of(new io.github.sashirestela.openai.common.tool.ToolCall(
                                0,
                                toolCall.getToolCallId(),
                                ToolType.FUNCTION,
                                new FunctionCall(toolCall.getToolName(), toolCall.getArguments()))));
                    }
                });
            }
        });
    }
}
