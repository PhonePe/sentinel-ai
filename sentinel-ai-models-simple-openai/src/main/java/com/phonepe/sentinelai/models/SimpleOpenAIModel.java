package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.agentmessages.*;
import com.phonepe.sentinelai.core.agentmessages.requests.*;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
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
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class SimpleOpenAIModel<M extends ChatCompletionServices> implements Model {
    private static final String OUTPUT_VARIABLE_NAME = "output";

    @Value
    private static class AgentMessages {
        ArrayList<ChatMessage> openAiMessages;
        ArrayList<AgentMessage> allMessages;
        ArrayList<AgentMessage> newMessages;
    }

    @UtilityClass
    private static final class FinishReasons {
        public static final String STOP = "stop";
        public static final String FUNCTION_CALL = "function_call";
        public static final String TOOL_CALLS = "tool_calls";
        public static final String LENGTH = "length";
        public static final String CONTENT_FILTER = "content_filter";
    }

    private final String modelName;
    private final M openAIProvider;
    private final ObjectMapper mapper;
    private final ParameterMapper parameterMapper;

    public SimpleOpenAIModel(String modelName, M openAIProvider, ObjectMapper mapper) {
        this.modelName = modelName;
        this.openAIProvider = openAIProvider;
        this.mapper = mapper;
        this.parameterMapper = new ParameterMapper(mapper);
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> CompletableFuture<DirectRunOutput> runDirect(
            ModelSettings modelSettings,
            ExecutorService executorService,
            String prompt,
            AgentExtension.AgentExtensionOutputDefinition outputDefinition,
            List<AgentMessage> messages) {
        final var openAiMessages = new ArrayList<>(convertToOpenAIMessages(messages));
        final var stats = new ModelUsageStats();
        return CompletableFuture.supplyAsync(() -> {
            DirectRunOutput output = null;
            do {
                final var builder = createChatRequestBuilder(openAiMessages);
                applyModelSettings(modelSettings, builder, Map.of());
                if (outputDefinition != null) {
                    builder.responseFormat(ResponseFormat.jsonSchema(ResponseFormat.JsonSchema.builder()
                                                                             .name(OUTPUT_VARIABLE_NAME)
                                                                             .schema(outputDefinition.getSchema())
                                                                             .strict(true)
                                                                             .build()));
                }
                stats.incrementRequestsForRun();
                final var request = builder.build();
                logModelRequest(request);
                final var completionResponse = openAIProvider.chatCompletions()
                        .create(request)
                        .join();
                logModelResponse(completionResponse);
                mergeUsage(stats, completionResponse.getUsage());
                final var response = extractResponse(completionResponse);
                if (null == response) {
                    return DirectRunOutput.error(stats, SentinelError.error(ErrorType.NO_RESPONSE));
                }
                final var message = response.getMessage();
                output = switch (response.getFinishReason()) {
                    case FinishReasons.STOP -> processOutput(message, stats, outputDefinition != null);
                    case FinishReasons.FUNCTION_CALL,
                         FinishReasons.TOOL_CALLS -> DirectRunOutput.error(
                            stats,
                            SentinelError.error(
                                    ErrorType.TOOL_CALL_PERMANENT_FAILURE, "Tools calls are not supported"));
                    case FinishReasons.LENGTH -> DirectRunOutput.error(stats,
                                                                       SentinelError.error(ErrorType.LENGTH_EXCEEDED));
                    case FinishReasons.CONTENT_FILTER -> DirectRunOutput.error(stats,
                                                                               SentinelError.error(ErrorType.FILTERED));
                    default -> DirectRunOutput.error(
                            stats, SentinelError.error(ErrorType.UNKNOWN_FINISH_REASON, response.getFinishReason()));
                };
            } while (output == null
                    || (output.getData() == null
                    && (output.getError() == null || output.getError().getErrorType().equals(ErrorType.SUCCESS))));
            return output;
        }, executorService);
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessages(
            AgentRunContext<R> context,
            JsonNode responseSchema,
            Map<String, ExecutableTool> tools,
            ToolRunner<R> toolRunner,
            List<AgentExtension<R,T,A>> extensions,
            A agent) {
        final var oldMessages = context.getOldMessages();
        final var modelSettings = context.getAgentSetup().getModelSettings();
        //This keeps getting
        // augmented with tool calls and reused across all iterations
        final var openAiMessages = new ArrayList<>(convertToOpenAIMessages(oldMessages));

        //There are for final model response
        final var allMessages = new ArrayList<>(oldMessages);
        final var newMessages = new ArrayList<AgentMessage>();

        //Stats for the run
        final var stats = new ModelUsageStats();

        return CompletableFuture.supplyAsync(() -> {
            ModelOutput output = null;
            do {
                final var builder = createChatRequestBuilder(openAiMessages);
                applyModelSettings(modelSettings, builder, tools);
                addToolList(tools, builder);
                builder.responseFormat(ResponseFormat.jsonSchema(structuredOutputSchema(responseSchema,
                                                                                        extensions,
                                                                                        context.getProcessingMode())));
                raiseMessageSentEvent(context, agent, oldMessages);
                final var stopwatch = Stopwatch.createStarted();
                stats.incrementRequestsForRun();

                final var request = builder.build();
                logModelRequest(request);
                final var completionResponse = openAIProvider.chatCompletions()
                        .create(request)
                        .join(); //TODO::CATCH EXCEPTIONS LIKE 429 etc
                logModelResponse(completionResponse);
                mergeUsage(stats, completionResponse.getUsage());
                final var response = extractResponse(completionResponse);
                if (null == response) {
                    return ModelOutput.error(oldMessages, stats, SentinelError.error(ErrorType.NO_RESPONSE));
                }
                final var message = response.getMessage();
                output = switch (response.getFinishReason()) {
                    case FinishReasons.STOP -> processOutput(context,
                                                             extensions,
                                                             agent,
                                                             message,
                                                             oldMessages,
                                                             stats,
                                                             allMessages,
                                                             newMessages,
                                                             stopwatch);
                    case FinishReasons.FUNCTION_CALL, FinishReasons.TOOL_CALLS -> {
                        final var toolCalls = Objects.requireNonNullElseGet(message.getToolCalls(),
                                                                            List::<io.github.sashirestela.openai.common.tool.ToolCall>of);

                        if (!toolCalls.isEmpty()) {
                            handleToolCalls(agent,
                                            context,
                                            tools,
                                            toolRunner,
                                            toolCalls,
                                            new AgentMessages(openAiMessages, allMessages, newMessages),
                                            stats,
                                            stopwatch);
                        }
                        yield null;
                    }
                    case FinishReasons.LENGTH -> ModelOutput.error(
                            oldMessages,
                            stats,
                            SentinelError.error(ErrorType.LENGTH_EXCEEDED));
                    case FinishReasons.CONTENT_FILTER -> ModelOutput.error(
                            oldMessages,
                            stats,
                            SentinelError.error(ErrorType.FILTERED));
                    default -> ModelOutput.error(
                            oldMessages,
                            stats,
                            SentinelError.error(ErrorType.UNKNOWN_FINISH_REASON, response.getFinishReason()));
                };
            } while (output == null || (output.getData() == null && output.getError() == null));
            return output;
        }, context.getAgentSetup().getExecutorService());
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessagesStreaming(
            AgentRunContext<R> context,
            Map<String, ExecutableTool> tools,
            ToolRunner<R> toolRunner,
            List<AgentExtension<R,T,A>> extensions,
            A agent,
            Consumer<byte[]> streamHandler) {
        final var oldMessages = context.getOldMessages();
        final var modelSettings = context.getAgentSetup().getModelSettings();
        //This keeps getting
        // augmented with tool calls and reused across all iterations
        final var openAiMessages = new ArrayList<>(convertToOpenAIMessages(oldMessages));

        //There are for final model response
        final var allMessages = new ArrayList<>(oldMessages);
        final var newMessages = new ArrayList<AgentMessage>();

        //Stats for the run
        final var stats = new ModelUsageStats();
        return CompletableFuture.supplyAsync(() -> {
            ModelOutput output = null;
            do {
                final var builder = createChatRequestBuilder(openAiMessages);
                applyModelSettings(modelSettings, builder, tools);
                addToolList(tools, builder);
                builder.responseFormat(ResponseFormat.jsonSchema(structuredOutputSchema(schema(String.class),
                                                                                        extensions,
                                                                                        context.getProcessingMode())));
                raiseMessageSentEvent(context, agent, oldMessages);
                final var stopwatch = Stopwatch.createStarted();
                stats.incrementRequestsForRun();

                final var completionResponseStream = openAIProvider.chatCompletions()
                        .createStream(builder.build())
                        .join();
                //We use the following to merge the pieces of response we get from stream into final output
                final var responseData = new StringBuilder();
                //We use the following to cobble together the fragment of tool call objects we get from the stream
                final var toolCallData = new HashMap<Integer, io.github.sashirestela.openai.common.tool.ToolCall>();

                final var outputs = completionResponseStream
                        .map(completionResponse -> {
                            logModelResponse(completionResponse);
                            mergeUsage(stats, completionResponse.getUsage());
                            final var response = extractResponse(completionResponse);
                            if (null == response) {
                                return null; //No response received yet, continue to next chunk
                            }
                            final var message = response.getMessage();
                            final var finishReason = response.getFinishReason();
                            if (Strings.isNullOrEmpty(finishReason)) {
                                //We must either have received content or some data in tool calls or both
                                if (null != message.getContent()) {
                                    responseData.append(message.getContent());
                                    streamHandler.accept(message.getContent().getBytes(StandardCharsets.UTF_8));
                                }
                                final var toolCalls = Objects.requireNonNullElseGet(message.getToolCalls(),
                                                                                    List::<io.github.sashirestela.openai.common.tool.ToolCall>of);
                                if (!toolCalls.isEmpty()) {
                                    // Caution: the following is not for people with weak constitution
                                    // The api sends fully formed objects with partial data in the field (I kid you not)
                                    // So we try to assemble the pieces together to form a complete object
                                    // I am not proud of having done this but like Bruce Willis says in die hard ...:
                                    // somebody has to do it
                                    toolCalls.forEach(call -> {
                                        var node = toolCallData.compute(
                                                call.getIndex(),
                                                (idx, existing) -> mergeToolCallFragment(existing, call));
                                        logDataTrace("Function till now: {}", node);
                                    });
                                }
                                return null; //Continue to next chunk
                            }
                            //Model has stopped for some reason. Find out reason and handle
                            return switch (finishReason) {
                                case FinishReasons.STOP -> processStreamingOutput(context,
                                                                                  extensions,
                                                                                  agent,
                                                                                  message,
                                                                                  oldMessages,
                                                                                  stats,
                                                                                  responseData,
                                                                                  allMessages,
                                                                                  newMessages,
                                                                                  stopwatch);
                                case FinishReasons.FUNCTION_CALL, FinishReasons.TOOL_CALLS -> {
                                    //Model is waiting for us to run tools and respond back
                                    final var toolCalls = toolCallData.values()
                                            .stream()
                                            .sorted(Comparator.comparing(io.github.sashirestela.openai.common.tool.ToolCall::getIndex))
                                            .toList();

                                    if (!toolCalls.isEmpty()) {
                                        handleToolCalls(agent, context,
                                                        tools,
                                                        toolRunner,
                                                        toolCalls,
                                                        new AgentMessages(openAiMessages,
                                                                          allMessages,
                                                                          newMessages),
                                                        stats,
                                                        stopwatch);
                                    }
                                    yield null; //Continue to next chunk
                                }
                                case FinishReasons.LENGTH -> ModelOutput.error(
                                        oldMessages,
                                        stats,
                                        SentinelError.error(ErrorType.LENGTH_EXCEEDED));
                                case FinishReasons.CONTENT_FILTER -> ModelOutput.error(
                                        oldMessages,
                                        stats,
                                        SentinelError.error(ErrorType.FILTERED));
                                default -> ModelOutput.error(
                                        oldMessages,
                                        stats,
                                        SentinelError.error(ErrorType.UNKNOWN_FINISH_REASON, finishReason));
                            };
                        })
                        .filter(Objects::nonNull)
                        .toList();
                //NOTE::DO NOT MERGE THE STREAM WITH BELOW
                //The flow is intentionally done this way
                // This needs to be done in two steps to ensure all chunks are consumed. Otherwise, some stuff like
                // usage etc. will get missed. Usage for example comes only after the full response is received.
                output = outputs.stream().findAny().orElse(null);
            } while (output == null || (output.getData() == null && output.getError() == null));
            return output;
        }, context.getAgentSetup().getExecutorService());
    }

    private static Chat.Choice extractResponse(Chat completionResponse) {
        return completionResponse
                .getChoices()
                .stream()
                .findFirst()
                .orElse(null);
    }

    private static io.github.sashirestela.openai.common.tool.ToolCall mergeToolCallFragment(
            io.github.sashirestela.openai.common.tool.ToolCall existing,
            io.github.sashirestela.openai.common.tool.ToolCall call) {
        if (null == existing) {
            return call;
        }
        final var id = !Strings.isNullOrEmpty(call.getId()) ? call.getId()
                                                            :
                       existing.getId();

        final var function = null != existing.getFunction()
                             ? existing.getFunction() : new FunctionCall();
        if (!Strings.isNullOrEmpty(call.getFunction().getName())) {
            function.setName(function.getName() + call.getFunction()
                    .getName());
        }
        if (!Strings.isNullOrEmpty(call.getFunction().getArguments())) {
            function.setArguments(function.getArguments() + call.getFunction()
                    .getArguments());
        }
        return new io.github.sashirestela.openai.common.tool.ToolCall(
                existing.getIndex(),
                id,
                existing.getType(),
                function);
    }

    private DirectRunOutput processOutput(ChatMessage.ResponseMessage message, ModelUsageStats stats, boolean process) {
        final var refusal = message.getRefusal();
        if (!Strings.isNullOrEmpty(refusal)) {
            return DirectRunOutput.error(stats,
                                         SentinelError.error(ErrorType.REFUSED, refusal));
        }
        final var content = message.getContent();
        if (!Strings.isNullOrEmpty(content)) {
            try {
                return DirectRunOutput.success(stats,
                                               process ? mapper.readTree(content)
                                                       : mapper.createObjectNode().textNode(content));
            }
            catch (JsonProcessingException e) {
                return DirectRunOutput.error(stats,
                                             SentinelError.error(ErrorType.JSON_ERROR, e));
            }
        }
        return DirectRunOutput.error(stats, SentinelError.error(ErrorType.NO_RESPONSE));
    }

    @SuppressWarnings("java:S107")
    private <R, T, A extends Agent<R, T, A>> ModelOutput processOutput(
            AgentRunContext<R> context,
            List<AgentExtension<R,T,A>> extensions,
            A agent,
            ChatMessage.ResponseMessage message,
            List<AgentMessage> oldMessages,
            ModelUsageStats stats,
            ArrayList<AgentMessage> allMessages,
            ArrayList<AgentMessage> newMessages,
            Stopwatch stopwatch) {
        final var refusal = message.getRefusal();
        if (!Strings.isNullOrEmpty(refusal)) {
            return ModelOutput.error(oldMessages,
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
                return ModelOutput.success(convertToResponse(
                                                   content,
                                                   extensions,
                                                   agent,
                                                   context.getProcessingMode()),
                                           newMessages,
                                           allMessages,
                                           stats);
            }
            catch (JsonProcessingException e) {
                return ModelOutput.error(oldMessages,
                                         stats,
                                         SentinelError.error(ErrorType.JSON_ERROR, e));
            }
        }
        return ModelOutput.error(oldMessages, stats, SentinelError.error(ErrorType.NO_RESPONSE));
    }

    /*
     * Process the streaming output from the model. This method is called when the model sends a response in chunks.
     */
    @SuppressWarnings("java:S107")
    private <R, T, A extends Agent<R, T, A>> @NonNull ModelOutput processStreamingOutput(
            AgentRunContext<R> context,
            List<AgentExtension<R,T,A>> extensions,
            A agent,
            ChatMessage.ResponseMessage message,
            List<AgentMessage> oldMessages,
            ModelUsageStats stats,
            StringBuilder responseData,
            ArrayList<AgentMessage> allMessages,
            ArrayList<AgentMessage> newMessages,
            Stopwatch stopwatch) {
        //Model has sent all response
        final var refusal = message.getRefusal();
        if (!Strings.isNullOrEmpty(refusal)) {
            return ModelOutput.error(oldMessages,
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
            try {
                return ModelOutput.success(convertToResponse(
                                                   content,
                                                   extensions,
                                                   agent,
                                                   context.getProcessingMode()),
                                           newMessages,
                                           allMessages,
                                           stats);
            }
            catch (JsonProcessingException e) {
                return ModelOutput.error(oldMessages,
                                         stats,
                                         SentinelError.error(ErrorType.JSON_ERROR, e));
            }
        }

        return ModelOutput.error(oldMessages,
                                 stats,
                                 SentinelError.error(ErrorType.NO_RESPONSE));
    }

    private ChatRequest.ChatRequestBuilder createChatRequestBuilder(ArrayList<ChatMessage> openAiMessages) {
        return ChatRequest.builder()
                .messages(openAiMessages)
                .model(modelName)
                .n(1);
    }

    private void addToolList(Map<String, ExecutableTool> tools, ChatRequest.ChatRequestBuilder requestBuilder) {
        if (!tools.isEmpty()) {
            requestBuilder.tools(tools.values()
                                         .stream()
                                         .map(tool -> {
                                             final var toolDefinition = tool.getToolDefinition();
                                             return new Tool(
                                                     ToolType.FUNCTION,
                                                     new Tool.ToolFunctionDef(toolDefinition.getId(),
                                                                              toolDefinition.getDescription(),
                                                                              tool.accept(parameterMapper),
                                                                              toolDefinition.isStrictSchema()));
                                         })
                                         .toList());
        }
    }

    private void logModelRequest(Object node) {
        logDataDebug("Request to model: {}", node);
    }

    private void logModelResponse(Object node) {
        logDataDebug("Response from model: {}", node);
    }

    private void logDataDebug(String fmtStr, Object node) {
        if (log.isDebugEnabled()) {
            try {
                log.debug(fmtStr, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
            }
            catch (JsonProcessingException e) {
                //Do nothing
            }
        }
    }

    private void logDataTrace(String fmtStr, Object node) {
        if (log.isTraceEnabled()) {
            try {
                log.trace(fmtStr, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node));
            }
            catch (JsonProcessingException e) {
                //Do nothing
            }
        }
    }

    public static void mergeUsage(ModelUsageStats stats, Usage usage) {
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

    /**
     * Apply model settings to the request builder.
     *
     * @param modelSettings Model settings to apply
     * @param builder       ChatRequest builder to apply settings to
     * @param tools         List of available tools
     */
    private static void applyModelSettings(
            ModelSettings modelSettings,
            ChatRequest.ChatRequestBuilder builder,
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


    /**
     * Handles tool calls in the model run. Runs tools and adds the responses to the messages.
     *
     * @param <R>           Request type for agent
     * @param <T>           Response type for agent
     * @param <A>           Agent type
     * @param agent         Agent instance to raise events for tool calls
     * @param context       AgentRunContext containing the context of the agent run.
     * @param tools         Map of available tools
     * @param toolRunner    Actual tool executor
     * @param toolCalls     Tool calls as received from model
     * @param agentMessages Messages to be updated with tool call messages
     * @param stats         Model usage stats to be updated with tool call usage
     * @param stopwatch     Stopwatch to measure time taken for tool calls
     */
    private static <R, T, A extends Agent<R, T, A>> void handleToolCalls(
            A agent,
            AgentRunContext<R> context,
            Map<String, ExecutableTool> tools,
            ToolRunner<R> toolRunner,
            List<io.github.sashirestela.openai.common.tool.ToolCall> toolCalls,
            AgentMessages agentMessages,
            ModelUsageStats stats,
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
                        }, context.getAgentSetup().getExecutorService()))
                .toList();
        log.debug("Running {} tool calls in parallel", jobs.size());
        jobs.stream()
                .map(CompletableFuture::join)
                .forEach(pair -> {
                    final var toolCallMessage = pair.getFirst();
                    final var toolCallResponse = pair.getSecond();
                    if (toolCallResponse.isSuccess()) {
                        log.debug("Tool call {} Successful. Name: {} Arguments: {} Response: {}",
                                  toolCallMessage.getToolCallId(),
                                  toolCallMessage.getToolName(),
                                  toolCallMessage.getArguments(),
                                  toolCallResponse.getResponse());
                    }
                    else {
                        log.error("Tool call {} Failed:. Name: {} Arguments: {} Error: {} -> {}",
                                  toolCallMessage.getToolCallId(),
                                  toolCallMessage.getToolName(),
                                  toolCallMessage.getArguments(),
                                  toolCallResponse.getErrorType(),
                                  toolCallResponse.getResponse());
                    }
                    agentMessages.getOpenAiMessages().add(convertIndividualMessageToOpenIDFormat(toolCallMessage));
                    agentMessages.getOpenAiMessages().add(convertIndividualMessageToOpenIDFormat(toolCallResponse));
                    agentMessages.getAllMessages().add(toolCallMessage);
                    agentMessages.getNewMessages().add(toolCallMessage);
                    raiseMessageReceivedEvent(context, agent, toolCallMessage, stopwatch);
                    agentMessages.getAllMessages().add(toolCallResponse);
                    agentMessages.getNewMessages().add(toolCallResponse);
                    stats.incrementToolCallsForRun();
                });
    }

    /**
     * Convert content to response node. Response is first converted to a map of the core output and those requested
     * by the different extensions. Once done the output is saved and extensions are given their respective data for
     * consumption.
     *
     * @return Final response node containing the core output from the model
     * @throws JsonProcessingException To be caught and handled at call site
     */
    private <R, T, A extends Agent<R, T, A>> JsonNode convertToResponse(
            String content,
            List<AgentExtension<R,T,A>> extensions, A agent,
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
        return outputNode.get(OUTPUT_VARIABLE_NAME);
    }

    /**
     * Creates a structured output schema for the model output. This will be set to a map containing the core output
     * schema with the map key output and other entries come from the extensions.
     *
     * @param outputSchema   The schema for the core output
     * @param extensions     List of agent extensions that might have their own output schemas
     * @param processingMode Processing mode for the agent run
     * @return ResponseFormat.JsonSchema containing the structured output schema
     */
    private <R, T, A extends Agent<R, T, A>> ResponseFormat.JsonSchema structuredOutputSchema(
            JsonNode outputSchema,
            List<AgentExtension<R,T,A>> extensions,
            ProcessingMode processingMode) {
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        final var fields = mapper.createArrayNode();
        schema.set("required", fields);
        final var propertiesNode = mapper.createObjectNode();
        schema.set("properties", propertiesNode);

        fields.add(OUTPUT_VARIABLE_NAME);
        propertiesNode.set(OUTPUT_VARIABLE_NAME, outputSchema);
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


    /**
     * Converts sentinel messages to OpenAI message format.
     *
     * @param agentMessages List of sentinel messages to convert
     * @return List of OpenAI messages
     */
    private List<ChatMessage> convertToOpenAIMessages(List<AgentMessage> agentMessages) {
        return Objects.requireNonNullElseGet(agentMessages, java.util.List::<AgentMessage>of)
                .stream()
                .map(SimpleOpenAIModel::convertIndividualMessageToOpenIDFormat)
                .toList();

    }

    /**
     * Converts an individual Sentinel AgentMessage to OpenAI ChatMessage format.
     *
     * @param agentMessage Message to convert
     * @return OpenAI ChatMessage representation of the AgentMessage
     */
    private static ChatMessage convertIndividualMessageToOpenIDFormat(AgentMessage agentMessage) {
        return agentMessage.accept(new AgentMessageVisitor<>() {
            @Override
            public ChatMessage visit(AgentRequest request) {
                return request.accept(new AgentRequestVisitor<>() {
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

            @Override
            public ChatMessage visit(AgentGenericMessage genericMessage) {
                return genericMessage.accept(new AgentGenericMessageVisitor<>() {
                    @Override
                    public ChatMessage visit(GenericText genericText) {
                        return switch (genericText.getRole()) {
                            case SYSTEM -> ChatMessage.SystemMessage.of(genericText.getText());
                            case USER -> ChatMessage.UserMessage.of(genericText.getText());
                            case ASSISTANT -> ChatMessage.AssistantMessage.of(genericText.getText());
                            case TOOL_CALL -> throw new UnsupportedOperationException(
                                    "Tool calls are unsupported in this context");
                        };
                    }

                    @Override
                    public ChatMessage visit(GenericResource genericResource) {
                        return switch (genericResource.getRole()) {
                            case SYSTEM -> ChatMessage.SystemMessage.of(genericResource.getSerializedJson());
                            case USER -> ChatMessage.UserMessage.of(genericResource.getSerializedJson());
                            case ASSISTANT -> ChatMessage.AssistantMessage.of(genericResource.getSerializedJson());
                            case TOOL_CALL -> throw new UnsupportedOperationException(
                                    "Tool calls are unsupported in this context");
                        };
                    }
                });
            }
        });
    }
}
