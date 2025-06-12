package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.*;
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
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ParameterMapper;
import com.phonepe.sentinelai.core.utils.Pair;
import io.github.sashirestela.openai.common.ResponseFormat;
import io.github.sashirestela.openai.common.Usage;
import io.github.sashirestela.openai.common.function.FunctionCall;
import io.github.sashirestela.openai.common.tool.Tool;
import io.github.sashirestela.openai.common.tool.ToolType;
import io.github.sashirestela.openai.domain.chat.Chat;
import io.github.sashirestela.openai.domain.chat.ChatMessage;
import io.github.sashirestela.openai.domain.chat.ChatRequest;
import io.github.sashirestela.openai.service.ChatCompletionServices;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import static com.phonepe.sentinelai.core.utils.AgentUtils.safeGetInt;
import static com.phonepe.sentinelai.core.utils.EventUtils.raiseMessageReceivedEvent;
import static com.phonepe.sentinelai.core.utils.EventUtils.raiseMessageSentEvent;
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
    ParameterMapper parameterMapper;

    public SimpleOpenAIModel(String modelName, M openAIProvider, ObjectMapper mapper) {
        this.modelName = modelName;
        this.openAIProvider = openAIProvider;
        this.mapper = mapper;
        this.parameterMapper = new ParameterMapper(mapper);
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> CompletableFuture<DirectRunOutput> runDirect(
            AgentRunContext<R> context,
            String prompt,
            AgentExtension.AgentExtensionOutputDefinition outputDefinition,
            List<AgentMessage> messages) {
        final var modelSettings = context.getAgentSetup().getModelSettings();
        final var openAiMessages = new ArrayList<>(convertToOpenAIMessages(messages));
        final var stats = new ModelUsageStats();
        return CompletableFuture.supplyAsync(() -> {
            DirectRunOutput output = null;
            do {
                final var builder = ChatRequest.builder()
                        .messages(openAiMessages)
                        .model(modelName)
                        .n(1);
                applyModelSettings(modelSettings, builder, Map.of());
                builder.responseFormat(ResponseFormat.jsonSchema(ResponseFormat.JsonSchema.builder()
                                                                         .name("output")
                                                                         .schema(outputDefinition.getSchema())
                                                                         .strict(true)
                                                                         .build()));
                final var stopwatch = Stopwatch.createStarted();
                stats.incrementRequestsForRun();
                final var completionResponse = openAIProvider.chatCompletions()
                        .create(builder.build())
                        .join();
                logResponse(completionResponse);
                mergeUsage(stats, completionResponse.getUsage());
                final var response = completionResponse
                        .getChoices()
                        .stream()
                        .findFirst()
                        .orElse(null);
                if (null == response) {
                    return DirectRunOutput.error(stats, SentinelError.error(ErrorType.NO_RESPONSE));
                }
                final var message = response.getMessage();
                output = switch (response.getFinishReason()) {
                    case "stop" -> {
                        final var refusal = message.getRefusal();
                        if (!Strings.isNullOrEmpty(refusal)) {
                            yield DirectRunOutput.error(stats,
                                                        SentinelError.error(ErrorType.REFUSED, refusal));
                        }
                        final var content = message.getContent();
                        if (!Strings.isNullOrEmpty(content)) {
                            try {
                                yield DirectRunOutput.success(stats, mapper.readTree(content));
                            }
                            catch (JsonProcessingException e) {
                                yield DirectRunOutput.error(stats,
                                                            SentinelError.error(ErrorType.JSON_ERROR, e));
                            }
                        }
                        yield DirectRunOutput.error(stats, SentinelError.error(ErrorType.NO_RESPONSE));
                    }
                    case "function_call", "tool_calls" -> DirectRunOutput.error(stats,
                                                                                SentinelError.error(ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                                                                    "Tools calls are " +
                                                                                                            "not " +
                                                                                                            "supported"));
                    case "length" -> DirectRunOutput.error(stats, SentinelError.error(ErrorType.LENGTH_EXCEEDED));
                    case "content_filter" -> DirectRunOutput.error(stats,
                                                                   SentinelError.error(ErrorType.FILTERED));
                    default -> DirectRunOutput.error(stats, SentinelError.error(ErrorType.UNKNOWN));
                };
            } while (output == null || (output.getData() == null && output.getError() == null));
            return output;
        }, context.getAgentSetup().getExecutorService());
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> CompletableFuture<AgentOutput<T>> exchange_messages(
            AgentRunContext<R> context,
            Class<T> responseType,
            Map<String, ExecutableTool> tools,
            Agent.ToolRunner<R> toolRunner,
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
                applyModelSettings(modelSettings, builder, tools);
                if (!tools.isEmpty()) {
                    builder.tools(tools.values()
                                          .stream()
                                          .map(tool -> {
                                              final var toolDefinition = tool.getToolDefinition();
                                              return new Tool(
                                                      ToolType.FUNCTION,
                                                      new Tool.ToolFunctionDef(toolDefinition.getName(),
                                                                               toolDefinition.getDescription(),
                                                                               tool.accept(parameterMapper),
                                                                               true));
                                          })
                                          .toList());
                }
                builder.responseFormat(ResponseFormat.jsonSchema(structuredOutputSchema(responseType,
                                                                                        extensions,
                                                                                        context.getProcessingMode())));
                raiseMessageSentEvent(context, agent, oldMessages);
                final var stopwatch = Stopwatch.createStarted();
                stats.incrementRequestsForRun();
                final var completionResponse = openAIProvider.chatCompletions()
                        .create(builder.build())
                        .join(); //TODO::CATCH EXCEPTIONS LIKE 429 etc
                logResponse(completionResponse);
                mergeUsage(stats, completionResponse.getUsage());
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
                                yield AgentOutput.success(convertToResponse(responseType,
                                                                            content,
                                                                            extensions,
                                                                            agent,
                                                                            context.getProcessingMode()),
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
                        final var toolCalls = Objects.requireNonNullElseGet(message.getToolCalls(),
                                                                            List::<io.github.sashirestela.openai.common.tool.ToolCall>of);
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
                        final var toolCalls = Objects.requireNonNullElseGet(message.getToolCalls(),
                                                                            List::<io.github.sashirestela.openai.common.tool.ToolCall>of);

                        if (!toolCalls.isEmpty()) {
                            final var toolCallResponse = handleToolCalls(context,
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
                            if (toolCallResponse != null) { //This is non-null only in case of errors
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

    @Override
    public <R, T, A extends Agent<R, T, A>> CompletableFuture<AgentOutput<byte[]>> exchange_messages_streaming(
            AgentRunContext<R> context,
            Map<String, ExecutableTool> tools,
            Agent.ToolRunner<R> toolRunner,
            List<AgentExtension> extensions,
            A agent,
            Consumer<byte[]> streamHandler) {
        final var oldMessages = context.getOldMessages();
        final var modelSettings = context.getAgentSetup().getModelSettings();
        final var openAiMessages = new ArrayList<>(convertToOpenAIMessages(oldMessages));
        final var allMessages = new ArrayList<>(oldMessages);
        final var newMessages = new ArrayList<AgentMessage>();
        final var stats = new ModelUsageStats();
        return CompletableFuture.supplyAsync(() -> {
            AgentOutput<byte[]> output = null;
            do {
                final var builder = ChatRequest.builder()
                        .messages(openAiMessages)
                        .model(modelName)
                        .n(1);
                applyModelSettings(modelSettings, builder, tools);
                if (!tools.isEmpty()) {
                    builder.tools(tools.values()
                                          .stream()
                                          .map(tool -> {
                                              final var toolDefinition = tool.getToolDefinition();
                                              final var params = tool.accept(parameterMapper);
                                              return new Tool(
                                                      ToolType.FUNCTION,
                                                      new Tool.ToolFunctionDef(toolDefinition.getName(),
                                                                               toolDefinition.getDescription(),
                                                                               params,
                                                                               true));
                                          })
                                          .toList());
                }
                raiseMessageSentEvent(context, agent, oldMessages);
                final var stopwatch = Stopwatch.createStarted();
                stats.incrementRequestsForRun();

                final var completionResponseStream = openAIProvider.chatCompletions()
                        .createStream(builder.build())
                        .join();
                var responseData = new StringBuilder();
                var toolCallData = new HashMap<Integer, io.github.sashirestela.openai.common.tool.ToolCall>();
                final var outputs = completionResponseStream
                        .<AgentOutput<byte[]>>map(completionResponse -> {
                            logResponse(completionResponse);
                            final var usage = completionResponse.getUsage();
                            if (null != usage) {
                                stats.incrementRequestTokens(safeGetInt(usage::getPromptTokens))
                                        .incrementResponseTokens(safeGetInt(usage::getCompletionTokens))
                                        .incrementTotalTokens(safeGetInt(usage::getTotalTokens));
                                final var promptTokensDetails = usage.getPromptTokensDetails();
                                if (promptTokensDetails != null) {
                                    stats.getRequestTokenDetails()
                                            .incrementAudioTokens(safeGetInt(promptTokensDetails::getAudioTokens))
                                            .incrementCachedTokens(safeGetInt(promptTokensDetails::getCachedTokens));
                                }
                                final var completionTokensDetails = usage.getCompletionTokensDetails();
                                if (completionTokensDetails != null) {
                                    stats.getResponseTokenDetails()
                                            .incrementAudioTokens(safeGetInt(completionTokensDetails::getAudioTokens))
                                            .incrementReasoningTokens(safeGetInt(completionTokensDetails::getReasoningTokens));
                                }
                                log.debug("Stats: {}", stats);
                            }
                            final var response = completionResponse
                                    .getChoices()
                                    .stream()
                                    .findFirst()
                                    .orElse(null);
                            if (null == response) {
                                return null;
                            }
                            final var message = response.getMessage();
                            final var finishReason = response.getFinishReason();
                            if (Strings.isNullOrEmpty(finishReason)) {
                                if (null != message.getContent()) {
                                    responseData.append(message.getContent());
                                    streamHandler.accept(message.getContent().getBytes(StandardCharsets.UTF_8));
                                }
                                final var toolCalls = Objects.requireNonNullElseGet(message.getToolCalls(),
                                                                                    List::<io.github.sashirestela.openai.common.tool.ToolCall>of);
                                if (!toolCalls.isEmpty()) {
                                    toolCalls.forEach(call -> {
                                        var node = toolCallData.compute(
                                                call.getIndex(),
                                                (idx, existing) -> {
                                                    if (null == existing) {
                                                        return call;
                                                    }
                                                    final var id = !Strings.isNullOrEmpty(call.getId()) ? call.getId() : existing.getId();

                                                    final var function = null != existing.getFunction() ? existing.getFunction() : new FunctionCall();
                                                    if(!Strings.isNullOrEmpty(call.getFunction().getName())) {
                                                        function.setName(function.getName() + call.getFunction().getName());
                                                    }
                                                    if(!Strings.isNullOrEmpty(call.getFunction().getArguments())) {
                                                        function.setArguments(function.getArguments() + call.getFunction().getArguments());
                                                    }
                                                    return new io.github.sashirestela.openai.common.tool.ToolCall(
                                                            existing.getIndex(),
                                                            id,
                                                            existing.getType(),
                                                            function);
                                                });
                                        if(log.isTraceEnabled()) {
                                            try {
                                                    log.info("Function till now: {}", mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
                                            }
                                            catch (JsonProcessingException e) {
                                                //Do nothing
                                            }
                                        }
                                    });

                                }
                                return null;
                            }
                            return switch (finishReason) {
                                case "stop" -> {
                                    final var refusal = message.getRefusal();
                                    if (!Strings.isNullOrEmpty(refusal)) {
                                        yield AgentOutput.<byte[]>error(oldMessages,
                                                                        stats,
                                                                        SentinelError.error(ErrorType.REFUSED,
                                                                                            refusal));
                                    }
                                    final var content = responseData.toString();
                                    if (!Strings.isNullOrEmpty(content)) {
                                        final var newMessage = new Text(content);
                                        allMessages.add(newMessage);
                                        newMessages.add(newMessage);
                                        raiseMessageReceivedEvent(context, agent, newMessage, stopwatch);
                                        yield AgentOutput.success(content.getBytes(StandardCharsets.UTF_8),
                                                                  newMessages,
                                                                  allMessages,
                                                                  stats);
                                    }

                                    yield AgentOutput.<byte[]>error(oldMessages,
                                                                    stats,
                                                                    SentinelError.error(ErrorType.NO_RESPONSE));
                                }
                                case "function_call", "tool_calls" -> {
                                    final var toolCalls = toolCallData.values()
                                            .stream()
                                            .sorted(Comparator.comparing(io.github.sashirestela.openai.common.tool.ToolCall::getIndex))
                                            .toList();

                                    if (!toolCalls.isEmpty()) {
                                        final var toolCallResponse = (AgentOutput<byte[]>) handleToolCalls(context,
                                                                                                           tools,
                                                                                                           oldMessages,
                                                                                                           context.getAgentSetup()
                                                                                                                   .getExecutorService(),
                                                                                                           toolRunner,
                                                                                                           toolCalls,
                                                                                                           openAiMessages,
                                                                                                           allMessages,
                                                                                                           newMessages,
                                                                                                           stats,
                                                                                                           agent,
                                                                                                           stopwatch);
                                        if (toolCallResponse != null) { //This is non-null only in case of errors
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
                                default -> AgentOutput.error(oldMessages, stats,
                                                             SentinelError.error(ErrorType.UNKNOWN));
                            };
                        })
                        .filter(Objects::nonNull)
                        .toList();
                //This needs to be done in two steps to ensure all chunks are consumed. Otherwise some stuff like
                // usage etc will get missed. Usage for example comes only after the full response is received.
                output = outputs.stream().findAny().orElse(null);
            } while (output == null || (output.getData() == null && output.getError() == null));
            return output;
        }, context.getAgentSetup().getExecutorService());
    }

    private static void mergeUsage(ModelUsageStats stats, Usage usage) {
        if (null != usage) {
            stats.incrementRequestTokens(safeGetInt(usage::getPromptTokens))
                    .incrementResponseTokens(safeGetInt(usage::getCompletionTokens))
                    .incrementTotalTokens(safeGetInt(usage::getTotalTokens));
            final var promptTokensDetails = usage.getPromptTokensDetails();
            if (promptTokensDetails != null) {
                stats.getRequestTokenDetails()
                        .incrementAudioTokens(safeGetInt(promptTokensDetails::getAudioTokens))
                        .incrementCachedTokens(safeGetInt(promptTokensDetails::getCachedTokens));
            }
            final var completionTokensDetails = usage.getCompletionTokensDetails();
            if (completionTokensDetails != null) {
                stats.getResponseTokenDetails()
                        .incrementAudioTokens(safeGetInt(completionTokensDetails::getAudioTokens))
                        .incrementReasoningTokens(safeGetInt(completionTokensDetails::getReasoningTokens));
            }
        }
    }

    private void logResponse(Chat completionResponse) {
        if (log.isTraceEnabled()) {
            try {
                log.trace("Response from model: {}",
                          mapper.writerWithDefaultPrettyPrinter()
                                  .writeValueAsString(completionResponse));

            }
            catch (IOException e) {
                log.error("Error serializing response: ", e);
            }
        }
    }

    private static <R, T, A extends Agent<R, T, A>> AgentOutput<T> handleToolCalls(
            AgentRunContext<R> context,
            Map<String, ExecutableTool> tools,
            List<AgentMessage> oldMessages,
            ExecutorService executorService,
            Agent.ToolRunner<R> toolRunner,
            List<io.github.sashirestela.openai.common.tool.ToolCall> toolCalls,
            ArrayList<ChatMessage> openAiMessages,
            ArrayList<AgentMessage> allMessages,
            ArrayList<AgentMessage> newMessages,
            ModelUsageStats stats,
            A agent,
            Stopwatch stopwatch) {
        final var jobs = toolCalls.stream()
                .filter(toolCall -> !Strings.isNullOrEmpty(toolCall.getId()))
                .map(toolCall -> CompletableFuture.supplyAsync(
                        () -> {
                            final var toolCallMessage = new ToolCall(toolCall.getId(),
                                                                     toolCall.getFunction().getName(),
                                                                     toolCall.getFunction().getArguments());
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
                    raiseMessageReceivedEvent(context, agent, toolCallMessage, stopwatch);
                    allMessages.add(toolCallResponse);
                    newMessages.add(toolCallResponse);
                    stats.incrementToolCallsForRun();
                    return toolCallResponse;
                })
                .filter(r -> !r.isSuccess() && !r.getErrorType().isRetryable())
                .toList();

        if (!failedCalls.isEmpty()) {
            return AgentOutput.error(oldMessages,
                                     stats,
                                     SentinelError.error(ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                         failedCalls.stream()
                                                                 .map(ToolCallResponse::getToolName)
                                                                 .toList()));
        }
        return null;
    }

    private <R, T, A extends Agent<R, T, A>> T convertToResponse(
            Class<T> responseType, String content,
            List<AgentExtension> extensions, A agent,
            ProcessingMode processingMode) throws JsonProcessingException {
        final var outputNode = mapper.readTree(content);
        extensions.forEach(extension -> {
            final var outputDef = extension.outputSchema(processingMode).orElse(null);
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

    private ResponseFormat.JsonSchema structuredOutputSchema(
            final Class<?> clazz,
            List<AgentExtension> extensions,
            ProcessingMode processingMode) {
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
            final var outputDefinition = extension.outputSchema(processingMode).orElse(null);
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

    private static void applyModelSettings(
            ModelSettings modelSettings, ChatRequest.ChatRequestBuilder builder,
            Map<String, ExecutableTool> tools) {
        if (null == modelSettings) {
            log.debug("No model settings provided");
            return;
        }
        if (modelSettings.getMaxTokens() != null) {
            builder.maxCompletionTokens(modelSettings.getMaxTokens());
        }
        if (modelSettings.getTemperature() != null) {
            builder.temperature(Double.valueOf(modelSettings.getTemperature()));
        }
        if (modelSettings.getTopP() != null) {
            builder.topP(Double.valueOf(modelSettings.getTopP()));
        }
        if (modelSettings.getParallelToolCalls() != null && !tools.isEmpty()) {
            builder.parallelToolCalls(modelSettings.getParallelToolCalls());
        }
        if (modelSettings.getSeed() != null) {
            builder.seed(modelSettings.getSeed());
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
        return Objects.requireNonNullElseGet(oldMessages, java.util.List::<AgentMessage>of)
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
