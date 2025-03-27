package com.phonepe.sentinelai.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.*;
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
import com.phonepe.sentinelai.core.tools.CallableTool;
import com.phonepe.sentinelai.core.utils.EventUtils;
import com.phonepe.sentinelai.core.utils.Pair;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static com.phonepe.sentinelai.core.utils.JsonUtils.schema;
import static java.util.stream.Collectors.toMap;

/**
 *
 */
@Value
@Slf4j
public class OpenAIModel implements Model {
    private static final String OUTPUT_VARIABLE_NAME = "output";
    String modelName;
    OpenAIClient client;
    ObjectMapper mapper;

    @Override
    public <R, T, A extends Agent<R, T, A>> CompletableFuture<AgentOutput<T>> exchange_messages(
            AgentRunContext<R> context,
            Class<T> responseType,
            Map<String, CallableTool> tools,
            Agent.ToolRunner<R> toolRunner,
            List<AgentExtension> extensions,
            A agent) {
        final var oldMessages = context.getOldMessages();
        final var modelSettings = context.getAgentSetup().getModelSettings();
        final var openAiMessages = new ArrayList<>(convertToOpenAIMessages(oldMessages));
        final var allMessages = new ArrayList<>(oldMessages);
        final var newMessages = new ArrayList<AgentMessage>();
        final var stats = new ModelUsageStats();

//        logJsonTrace(openAiMessages);
        return CompletableFuture.supplyAsync(() -> {
            AgentOutput<T> output = null;
            do {
                final var completionCall = buildCompletionRequest(context,
                                                                         responseType,
                                                                         tools,
                                                                         extensions,
                                                                         agent,
                                                                         openAiMessages,
                                                                         modelSettings,
                                                                         oldMessages);
                final var stopwatch = Stopwatch.createStarted();
                final var completionResponse = client.chat()
                        .completions()
                        .create(completionCall);
                logJsonTrace(completionResponse);
                final var usage = completionResponse.usage().orElse(null);

                if (null != usage) {
                    //TODO UPDATE CONTEXT USAGE
                }
                final var choice = completionResponse.choices().stream().findFirst().orElse(null);
                if (choice == null) {
                    return AgentOutput.error(oldMessages, stats, SentinelError.error(ErrorType.NO_RESPONSE));
                }
                final var finishReason = choice.finishReason();
                final var message = choice.message();

                output = switch (finishReason.value()) {
                    case STOP -> {
                        final var refusal = message.refusal().orElse(null);
                        if (!Strings.isNullOrEmpty(refusal)) {
                            yield AgentOutput.error(oldMessages,
                                                    stats,
                                                    SentinelError.error(ErrorType.REFUSED, refusal));
                        }
                        final var content = message.content().orElse(null);
                        if (null != content) {
                            final var newMessage = new StructuredOutput(content);
                            allMessages.add(newMessage);
                            newMessages.add(newMessage);
                            EventUtils.raiseMessageReceivedEvent(context, agent, newMessage, stopwatch);
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
                        final var toolCalls =
                                message.toolCalls().orElse(List.<ChatCompletionMessageToolCall>of());
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
                    case FUNCTION_CALL, TOOL_CALLS -> {
                        final var toolCalls =
                                message.toolCalls().orElse(List.of());
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
                    case LENGTH -> AgentOutput.error(oldMessages,
                                                     stats,
                                                     SentinelError.error(ErrorType.LENGTH_EXCEEDED));
                    case CONTENT_FILTER -> AgentOutput.error(oldMessages,
                                                             stats,
                                                             SentinelError.error(ErrorType.FILTERED));
                    case _UNKNOWN -> AgentOutput.error(oldMessages, stats, SentinelError.error(ErrorType.UNKNOWN));
                };


            } while (output == null || output.getData() == null);
            return output;
        }, context.getAgentSetup().getExecutorService());
    }

    private <R, T, A extends Agent<R, T, A>> ChatCompletionCreateParams buildCompletionRequest(
            AgentRunContext<R> context,
            Class<T> responseType,
            Map<String, CallableTool> tools,
            List<AgentExtension> extensions,
            A agent,
            ArrayList<ChatCompletionMessageParam> openAiMessages,
            ModelSettings modelSettings,
            List<AgentMessage> oldMessages) {
        final var builder = ChatCompletionCreateParams.builder()
                .model(ChatModel.of(modelName))
                .messages(openAiMessages)
                .n(1);
        applyModelSettings(modelSettings, builder);
        if (!tools.isEmpty()) {
            builder.tools(tools.values().stream().map(OpenAIModel::functionCallSpec).toList());
        }

        final var jsonSchema = structuredOutputSchema(responseType, extensions);
        builder.responseFormat(jsonSchema);
        EventUtils.raiseMessageSentEvent(context, agent, oldMessages);
        return builder.build();
    }

    @SneakyThrows
    private void logJsonTrace(Object object) {
        log.trace("Messages: {}", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object));
    }

    private static <R, T, A extends Agent<R, T, A>> AgentOutput<T> handleToolCalls(
            AgentRunContext<R> context,
            Map<String, CallableTool> tools,
            List<AgentMessage> oldMessages,
            ExecutorService executorService,
            Agent.ToolRunner toolRunner,
            List<ChatCompletionMessageToolCall> toolCalls,
            ArrayList<ChatCompletionMessageParam> openAiMessages,
            ArrayList<AgentMessage> allMessages,
            ArrayList<AgentMessage> newMessages,
            ModelUsageStats stats,
            A agent,
            Stopwatch stopwatch) {
        final var jobs = toolCalls.stream()
                .map(toolCall -> CompletableFuture.supplyAsync(
                        () -> {
                            final var toolCallMessage = new ToolCall(toolCall.id(),
                                                                     toolCall.function().name(),
                                                                     toolCall.function().arguments());
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
                    EventUtils.raiseMessageReceivedEvent(context, agent, toolCallMessage, stopwatch);
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

    private static void applyModelSettings(ModelSettings modelSettings, ChatCompletionCreateParams.Builder builder) {
        if (null == modelSettings) {
            log.debug("No model settings provided");
            return;
        }
        if (modelSettings.getMaxTokens() != null) {
            builder.maxCompletionTokens(modelSettings.getMaxTokens());
        }
        if (modelSettings.getTemperature() != null) {
            builder.temperature(modelSettings.getTemperature());
        }
        if (modelSettings.getTopP() != null) {
            builder.topP(modelSettings.getTopP());
        }
        if (modelSettings.getParallelToolCalls() != null) {
            builder.parallelToolCalls(Boolean.TRUE.equals(modelSettings.getParallelToolCalls()));
        }
        if (modelSettings.getFrequencyPenalty() != null) {
            builder.frequencyPenalty(modelSettings.getFrequencyPenalty());
        }
        if (modelSettings.getPresencePenalty() != null) {
            builder.presencePenalty(modelSettings.getPresencePenalty());
        }
        if (modelSettings.getLogitBias() != null) {
            builder.logitBias(ChatCompletionCreateParams.LogitBias.builder()
                                      .putAllAdditionalProperties(modelSettings.getLogitBias()
                                                                          .entrySet()
                                                                          .stream()
                                                                          .collect(toMap(Map.Entry::getKey,
                                                                                         e -> JsonValue.from(e.getValue()))))
                                      .build());
        }
    }


    private List<ChatCompletionMessageParam> convertToOpenAIMessages(List<AgentMessage> oldMessages) {
        return oldMessages.stream()
                .map(OpenAIModel::convertIndividualMessageToOpenIDFormat)
                .toList();
    }

    @NotNull
    private static ChatCompletionMessageParam convertIndividualMessageToOpenIDFormat(final AgentMessage message) {
        return message.accept(new AgentMessageVisitor<ChatCompletionMessageParam>() {
            @Override
            public ChatCompletionMessageParam visit(AgentRequest request) {
                return request.accept(new AgentRequestVisitor<>() {
                    @Override
                    public ChatCompletionMessageParam visit(SystemPrompt systemPrompt) {
                        return ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder()
                                                                           .content(systemPrompt.getContent())
                                                                           .build());
                    }

                    @Override
                    public ChatCompletionMessageParam visit(UserPrompt userPrompt) {
                        return ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
                                                                         .content(userPrompt.getContent())
                                                                         .build());
                    }

                    @Override
                    public ChatCompletionMessageParam visit(ToolCallResponse toolCallResponse) {
                        return ChatCompletionMessageParam.ofTool(ChatCompletionToolMessageParam.builder()
                                                                         .toolCallId(toolCallResponse.getToolCallId())
                                                                         .role(JsonValue.from("tool"))
                                                                         .content(toolCallResponse.getResponse())
                                                                         .build());
                    }
                });
            }

            @Override
            public ChatCompletionMessageParam visit(AgentResponse response) {
                return response.accept(new AgentResponseVisitor<>() {
                    @Override
                    public ChatCompletionMessageParam visit(Text text) {
                        return ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder()
                                                                              .content(text.getContent())
                                                                              .build());
                    }

                    @Override
                    public ChatCompletionMessageParam visit(StructuredOutput structuredOutput) {
                        return ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder()
                                                                              .content(structuredOutput.getContent())
                                                                              .build());
                    }

                    @Override
                    public ChatCompletionMessageParam visit(ToolCall toolCall) {
                        return ChatCompletionMessageParam.ofAssistant(
                                ChatCompletionAssistantMessageParam.builder()
                                        .addToolCall(ChatCompletionMessageToolCall.builder()
                                                             .id(toolCall.getToolCallId())
                                                             .type(JsonValue.from("function"))
                                                             .function(ChatCompletionMessageToolCall.Function.builder()
                                                                               .name(toolCall.getToolName())
                                                                               .arguments(toolCall.getArguments())
                                                                               .build())
                                                             .build())
                                        .build());
                    }
                });
            }
        });
    }

    private <R, T, A extends Agent<R, T, A>>  T convertToResponse(
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

    private static ChatCompletionTool functionCallSpec(final CallableTool tool) {
        final var meta = tool.getToolDefinition();
        final var params = meta.getParameters()
                .values()
                .stream()
                .map(param -> Pair.of(param.getName(),
                                      schema(param.getType().getRawClass())))
                .collect(toMap(Pair::getFirst, Pair::getSecond));
        return ChatCompletionTool.builder()
                .function(FunctionDefinition.builder()
                                  .name(meta.getName())
                                  .description(meta.getDescription())
                                  .parameters(FunctionParameters.builder()
                                                      .putAdditionalProperty("type", JsonValue.from("object"))
                                                      .putAdditionalProperty("properties", JsonValue.from(params))
                                                      .putAdditionalProperty("additionalProperties",
                                                                             JsonValue.from(false))
                                                      .putAdditionalProperty("required", JsonValue.from(params.keySet()))
                                                      .build())
                                  .strict(true)
                                  .build())
                .build();

    }


    private ResponseFormatJsonSchema structuredOutputSchema(final Class<?> clazz, List<AgentExtension> extensions) {
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
        return ResponseFormatJsonSchema.builder()
                .jsonSchema(ResponseFormatJsonSchema.JsonSchema.builder()
                                    .name("model_output")
                                    .strict(true)
                                    .putAdditionalProperty("schema", JsonValue.fromJsonNode(schema))
                                    .build())
                .build();
    }
}
