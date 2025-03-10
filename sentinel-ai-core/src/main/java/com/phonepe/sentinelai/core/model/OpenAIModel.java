package com.phonepe.sentinelai.core.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.models.*;
import com.phonepe.sentinelai.core.agent.Agent;
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
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.Pair;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static java.util.stream.Collectors.toMap;

/**
 *
 */
@Value
@Slf4j
public class OpenAIModel implements Model {
    String modelName;
    OpenAIClient client;
    ObjectMapper mapper;

    @Override
    public <R, T, D> CompletableFuture<AgentOutput<T>> exchange_messages(
            ModelSettings modelSettings,
            AgentRunContext<D, R> context,
            Class<T> responseType,
            Map<String, CallableTool> tools,
            List<AgentMessage> oldMessages,
            ExecutorService executorService,
            Agent.ToolRunner toolRunner) {
        final var openAiMessages = new ArrayList<>(convertToOpenAIMessages(oldMessages));
        final var allMessages = new ArrayList<>(oldMessages);
        final var newMessages = new ArrayList<AgentMessage>();
        final var stats = new ModelUsageStats();
        //Setup the system and user prompts for this run
//        messages.add(ChatCompletionMessageParam.ofSystem(ChatCompletionSystemMessageParam.builder()
//                                                                 .content(systemPrompt)
//                                                                 .build()));
//        messages.add(ChatCompletionMessageParam.ofUser(ChatCompletionUserMessageParam.builder()
//                                                               .content(toStringContent(request))
//                                                               .build()));
        try {
            log.trace("Messages: " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(openAiMessages));
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return CompletableFuture.supplyAsync(() -> {
            var output = (AgentOutput<T>) null;
            do {
                final var builder = ChatCompletionCreateParams.builder()
                        .model(ChatModel.of(modelName))
                        .messages(openAiMessages)
                        .n(1);
                applyModelSettings(modelSettings, builder);
                if (!tools.isEmpty()) {
                    builder.tools(tools.values().stream().map(OpenAIModel::functionCallSpec).toList());
                }

                if (!responseType.equals(String.class)) {
                    builder.responseFormat(structuredOutput(responseType));
                }
                else {
                    builder.responseFormat(ResponseFormatText.builder().build());
                }

                final var completionCall = builder
                        .build();
                log.debug("Completion call: {}", completionCall);
                final var completionResponse = client.chat()
                        .completions()
                        .create(completionCall);
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
                            final var newMessage = responseType.isAssignableFrom(String.class)
                                                   ? new Text(content)
                                                   : new StructuredOutput(content);
                            allMessages.add(newMessage);
                            newMessages.add(newMessage);
                            try {
                                yield AgentOutput.success(convertToResponse(responseType, content),
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
                        yield AgentOutput.error(oldMessages, stats, SentinelError.error(ErrorType.NO_RESPONSE));

                    }
                    case FUNCTION_CALL, TOOL_CALLS -> {
                        final var toolCalls =
                                message.toolCalls().orElse(List.<ChatCompletionMessageToolCall>of());
                        final var jobs = toolCalls.stream()
                                .map(toolCall -> CompletableFuture.supplyAsync(
                                        () -> {
                                            final var toolCallMessage = new ToolCall(toolCall.id(),
                                                                                     toolCall.function().name(),
                                                                                     toolCall.function().arguments());
                                            final var toolCallResponse = toolRunner.runTool(context, tools, toolCallMessage);
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
                                    allMessages.add(toolCallResponse);
                                    newMessages.add(toolCallResponse);
                                    return toolCallResponse;
                                })
                                .filter(Predicates.not(ToolCallResponse::isSuccess))
                                .toList();

                        if (!failedCalls.isEmpty()) {
                            //TODO::DETERMINE IF CALLS HAVE FAILED PERMANENTLY
                            yield AgentOutput.error(oldMessages,
                                                    stats,
                                                    SentinelError.error(ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                                        failedCalls.stream()
                                                                                .map(ToolCallResponse::getToolName)
                                                                                .toList()));
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
        }, executorService);
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

//    private <T> AgentOutput<T> convertFromOpenAIMessages(
//            ChatCompletion.Choice choice,
//            List<AgentMessage> oldMessages) {
//        final var finishReason = choice.finishReason();
//        final var message = choice.message();
//        final var allMessages = new ArrayList<>(oldMessages);
//        final var newMessages = new ArrayList<AgentMessage>();
//        return switch (finishReason.value()) {
//            case STOP -> {
//                allMessages.add(ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder()
//                                                                               .role(JsonValue.from(
//                                                                                       "assistant"))
//                                                                               .refusal(message.refusal())
//                                                                               .content(message.content()
//                                                                                                .orElse(""))
//                                                                               .toolCalls(message.toolCalls()
//                                                                                                  .orElse(List.of()))
//                                                                               .build()));
//                final var refusal = message.refusal().orElse(null);
//                if (!Strings.isNullOrEmpty(refusal)) {
//                    yield new AgentOutput<>(null, null, "Execution refused: %s".formatted(refusal));
//                }
//                final var content = message.content().orElse(null);
//                if (null != content) {
//                    yield new Response<T>(convertToResponse(responseType, content), List.of(), "");
//                }
//                yield new Response<T>(null, null, "No response from model");
//            }
//            case LENGTH -> null;
//            case TOOL_CALLS -> null;
//            case CONTENT_FILTER -> null;
//            case FUNCTION_CALL -> null;
//            case _UNKNOWN -> null;
//        };
//
//    }

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
                        return ChatCompletionMessageParam.ofAssistant(ChatCompletionAssistantMessageParam.builder()
                                                                              .addToolCall(
                                                                                      ChatCompletionMessageToolCall.builder()
                                                                                              .id(toolCall.getToolCallId())
                                                                                              .type(JsonValue.from(
                                                                                                      "function"))
                                                                                              .function(
                                                                                                      ChatCompletionMessageToolCall.Function.builder()
                                                                                                              .name(toolCall.getToolName())
                                                                                                              .arguments(
                                                                                                                      toolCall.getArguments())
                                                                                                              .build())
                                                                                              .build())
                                                                              .build());
                    }
                });
            }
        });
    }

    private <T> T convertToResponse(Class<T> responseType, String content) throws JsonProcessingException {
        if (responseType.equals(String.class)) {
            return responseType.cast(content);
        }
        return mapper.readValue(content, responseType);
    }

    private static ChatCompletionTool functionCallSpec(final CallableTool tool) {
        final var meta = tool.getToolDefinition();
        final var params = meta.getParameters()
                .values()
                .stream()
                .map(param -> Pair.of(param.getName(),
                                      JsonUtils.schema(param.getType().getRawClass())))
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

    private static ResponseFormatJsonSchema structuredOutput(final Class<?> clazz) {
        return ResponseFormatJsonSchema.builder()
                .jsonSchema(ResponseFormatJsonSchema.JsonSchema.builder()
                                    .name(clazz.getSimpleName())
                                    .strict(true)
                                    .putAdditionalProperty("schema", JsonValue.fromJsonNode(JsonUtils.schema(clazz)))
                                    .build())
                .build();
    }
}
