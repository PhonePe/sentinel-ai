package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.*;
import com.phonepe.sentinelai.core.agentmessages.requests.*;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.*;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.ParameterMapper;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.Pair;
import io.github.sashirestela.openai.common.ResponseFormat;
import io.github.sashirestela.openai.common.Usage;
import io.github.sashirestela.openai.common.function.FunctionCall;
import io.github.sashirestela.openai.common.tool.Tool;
import io.github.sashirestela.openai.common.tool.ToolChoiceOption;
import io.github.sashirestela.openai.common.tool.ToolType;
import io.github.sashirestela.openai.domain.chat.Chat;
import io.github.sashirestela.openai.domain.chat.ChatMessage;
import io.github.sashirestela.openai.domain.chat.ChatRequest;
import io.github.sashirestela.openai.service.ChatCompletionServices;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static com.phonepe.sentinelai.core.utils.AgentUtils.safeGetInt;
import static com.phonepe.sentinelai.core.utils.EventUtils.*;

/**
 * Model implementation based on SimpleOpenAI client.
 * <p>
 * Please check <a href="https://github.com/sashirestela/simple-openai">Simple OpenAI Repo</a>
 * for details of client usage
 */
@Slf4j
public class SimpleOpenAIModel<M extends ChatCompletionServices> implements Model {

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
    private final SimpleOpenAIModelOptions modelOptions;

    public SimpleOpenAIModel(String modelName, M openAIProvider, ObjectMapper mapper) {
        this(modelName,
             openAIProvider,
             mapper,
             new SimpleOpenAIModelOptions(SimpleOpenAIModelOptions.ToolChoice.REQUIRED));
    }

    public SimpleOpenAIModel(
            final String modelName,
            final M openAIProvider,
            final ObjectMapper mapper,
            final SimpleOpenAIModelOptions modelOptions) {
        this.modelName = modelName;
        this.openAIProvider = openAIProvider;
        this.mapper = mapper;
        this.parameterMapper = new ParameterMapper(mapper);
        this.modelOptions = Objects.requireNonNullElse(
                modelOptions,
                new SimpleOpenAIModelOptions(SimpleOpenAIModelOptions.DEFAULT_TOOL_CHOICE));
    }

    @Override
    public CompletableFuture<ModelOutput> compute(
            ModelRunContext context,
            Collection<ModelOutputDefinition> outputDefinitions,
            List<AgentMessage> oldMessages,
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner) {
        final var agentSetup = context.getAgentSetup();
        final var modelSettings = agentSetup.getModelSettings();
        //This keeps getting
        // augmented with tool calls and reused across all iterations
        final var openAiMessages = new ArrayList<>(convertToOpenAIMessages(oldMessages));

        //There are for final model response
        final var allMessages = new ArrayList<>(oldMessages);
        final var newMessages = new ArrayList<AgentMessage>();

        //Stats for the run
        final var stats = new ModelUsageStats();
        final var outputGenerationMode
                = Objects.requireNonNullElse(agentSetup.getOutputGenerationMode(), OutputGenerationMode.TOOL_BASED);
        final var outputGenerator = Objects.requireNonNullElseGet(
                agentSetup.getOutputGenerationTool(), IdentityOutputGenerator::new);
        final var toolsForExecution = new HashMap<>(Objects.requireNonNullElseGet(tools, Map::of));
        final var generatedOutput = new AtomicReference<String>(null);
        final var schema = compliantSchema(outputDefinitions);
        if (outputGenerationMode.equals(OutputGenerationMode.TOOL_BASED)) {
            addOutputExtractionTool(toolsForExecution, schema, outputGenerator, generatedOutput);
        }
        return CompletableFuture.supplyAsync(() -> {
            ModelOutput output = null;
            do {
                generatedOutput.set(null);
                final var builder = createChatRequestBuilder(openAiMessages);
                applyModelSettings(modelSettings, builder, toolsForExecution);
                addToolList(toolsForExecution, builder);
                if (outputGenerationMode.equals(OutputGenerationMode.STRUCTURED_OUTPUT)) {
                    builder.responseFormat(ResponseFormat.jsonSchema(ResponseFormat.JsonSchema.builder()
                                                                             .name("model_output")
                                                                             .schema(schema)
                                                                             .strict(true)
                                                                             .build()));
                }
                builder.toolChoice(computeToolChoice(outputGenerationMode));
                raiseMessageSentEvent(context, oldMessages);
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
                    case FinishReasons.STOP -> {
                        final var refusal = message.getRefusal();
                        if (!Strings.isNullOrEmpty(refusal)) {
                            yield ModelOutput.error(oldMessages,
                                                    stats,
                                                    SentinelError.error(ErrorType.REFUSED, refusal));
                        }
                        final var toolCalls = Objects.requireNonNullElseGet(message.getToolCalls(),
                                                                            List::<io.github.sashirestela.openai.common.tool.ToolCall>of);

                        if (!toolCalls.isEmpty()) {
                            handleToolCalls(context,
                                            toolsForExecution,
                                            toolRunner,
                                            toolCalls,
                                            new AgentMessages(openAiMessages, allMessages, newMessages),
                                            stats,
                                            stopwatch);
                            if (generatedOutput.get() != null) {
                                //If the output generator was called, we use the generated output
                                yield processOutput(context,
                                                    generatedOutput.get(),
                                                    oldMessages,
                                                    stats,
                                                    allMessages,
                                                    newMessages,
                                                    stopwatch);
                            }
                        }
                        yield processOutput(context,
                                            message.getContent(),
                                            oldMessages,
                                            stats,
                                            allMessages,
                                            newMessages,
                                            stopwatch);
                    }
                    case FinishReasons.FUNCTION_CALL, FinishReasons.TOOL_CALLS -> {
                        final var toolCalls = Objects.requireNonNullElseGet(message.getToolCalls(),
                                                                            List::<io.github.sashirestela.openai.common.tool.ToolCall>of);

                        if (!toolCalls.isEmpty()) {
                            handleToolCalls(context,
                                            toolsForExecution,
                                            toolRunner,
                                            toolCalls,
                                            new AgentMessages(openAiMessages, allMessages, newMessages),
                                            stats,
                                            stopwatch);
                            if (generatedOutput.get() != null) {
                                //If the output generator was called, we use the generated output
                                yield processOutput(context,
                                                    generatedOutput.get(),
                                                    oldMessages,
                                                    stats,
                                                    allMessages,
                                                    newMessages,
                                                    stopwatch);
                            }
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
        }, agentSetup.getExecutorService());
    }

    @Override
    public CompletableFuture<ModelOutput> stream(
            ModelRunContext context,
            Collection<ModelOutputDefinition> outputDefinitions,
            List<AgentMessage> oldMessages,
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner,
            Consumer<byte[]> streamHandler) {
        return streamImpl(context,
                          outputDefinitions,
                          oldMessages,
                          tools,
                          toolRunner,
                          streamHandler,
                          Agent.StreamProcessingMode.TYPED);
    }

    @Override
    public CompletableFuture<ModelOutput> streamText(
            ModelRunContext context,
            List<AgentMessage> oldMessages,
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner,
            Consumer<byte[]> streamHandler) {
        return streamImpl(context,
                          List.of(),
                          oldMessages,
                          tools,
                          toolRunner,
                          streamHandler,
                          Agent.StreamProcessingMode.TEXT);
    }

    private CompletableFuture<ModelOutput> streamImpl(
            ModelRunContext context,
            Collection<ModelOutputDefinition> outputDefinitions,
            List<AgentMessage> oldMessages,
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner,
            Consumer<byte[]> streamHandler,
            Agent.StreamProcessingMode streamProcessingMode) {
        final var agentSetup = context.getAgentSetup();
        final var modelSettings = agentSetup.getModelSettings();
        //This keeps getting
        // augmented with tool calls and reused across all iterations
        final var openAiMessages = new ArrayList<>(convertToOpenAIMessages(oldMessages));

        //There are for final model response
        final var allMessages = new ArrayList<>(oldMessages);
        final var newMessages = new ArrayList<AgentMessage>();

        //Stats for the run
        final var stats = new ModelUsageStats();
        final var toolsForExecution = new HashMap<>(Objects.requireNonNullElseGet(tools, Map::of));
        final var outputGenerationMode
                = Objects.requireNonNullElse(agentSetup.getOutputGenerationMode(), OutputGenerationMode.TOOL_BASED);
        final var outputGenerator = Objects.requireNonNullElseGet(
                agentSetup.getOutputGenerationTool(), IdentityOutputGenerator::new);
        final var generatedOutput = new AtomicReference<String>(null);
        final var schema = compliantSchema(outputDefinitions);
        if (streamProcessingMode.equals(Agent.StreamProcessingMode.TYPED)
                && outputGenerationMode.equals(OutputGenerationMode.TOOL_BASED)) {
            addOutputExtractionTool(toolsForExecution, schema, outputGenerator, generatedOutput);
        }
        return CompletableFuture.supplyAsync(() -> {
            ModelOutput output = null;
            do {
                final var builder = createChatRequestBuilder(openAiMessages);
                applyModelSettings(modelSettings, builder, tools);
                addToolList(toolsForExecution, builder);
                if (streamProcessingMode.equals(Agent.StreamProcessingMode.TYPED)
                        && outputGenerationMode.equals(OutputGenerationMode.STRUCTURED_OUTPUT)) {
                    builder.responseFormat(ResponseFormat.jsonSchema(ResponseFormat.JsonSchema.builder()
                                                                             .name("model_output")
                                                                             .schema(schema)
                                                                             .strict(true)
                                                                             .build()));
                }
                builder.toolChoice(computeToolChoice(outputGenerationMode));
                final var stopwatch = Stopwatch.createStarted();
                stats.incrementRequestsForRun();

                final var request = builder.build();
                logModelRequest(request);
                final var completionResponseStream = openAIProvider.chatCompletions()
                        .createStream(request)
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
                                case FinishReasons.STOP -> {
                                    final var refusal = message.getRefusal();
                                    if (!Strings.isNullOrEmpty(refusal)) {
                                        yield ModelOutput.error(oldMessages,
                                                                stats,
                                                                SentinelError.error(ErrorType.REFUSED,
                                                                                    refusal));
                                    }
                                    // Output handling is a little different for streaming and non-streaming cases
                                    // For streaming it looks like VLLM etc. are not supporting tool calls properly
                                    // So we do the old-fashioned way and use fragments collected during streaming
                                    // to cobble together the final output
                                    if (streamProcessingMode.equals(Agent.StreamProcessingMode.TYPED)) {
                                        yield processOutput(context,
                                                            responseData.toString(),
                                                            //We just take what we gathered return that
                                                            oldMessages,
                                                            stats,
                                                            allMessages,
                                                            newMessages,
                                                            stopwatch);
                                    }
                                    else {
                                        yield processStreamingOutput(context,
                                                                     responseData.toString(),
                                                                     //We just take what we gathered return that
                                                                     oldMessages,
                                                                     stats,
                                                                     allMessages,
                                                                     newMessages,
                                                                     stopwatch);
                                    }
                                }
                                case FinishReasons.FUNCTION_CALL, FinishReasons.TOOL_CALLS -> {
                                    //Model is waiting for us to run tools and respond back
                                    final var toolCalls = toolCallData.values()
                                            .stream()
                                            .sorted(Comparator.comparing(io.github.sashirestela.openai.common.tool.ToolCall::getIndex))
                                            .toList();

                                    if (!toolCalls.isEmpty()) {
                                        handleToolCalls(context,
                                                        toolsForExecution,
                                                        toolRunner,
                                                        toolCalls,
                                                        new AgentMessages(openAiMessages, allMessages, newMessages),
                                                        stats,
                                                        stopwatch);
                                        if (generatedOutput.get() != null) {
                                            //If the output generator was called, we use the generated output
                                            if (streamProcessingMode.equals(Agent.StreamProcessingMode.TYPED)) {
                                                yield processOutput(context,
                                                                    generatedOutput.get(),
                                                                    oldMessages,
                                                                    stats,
                                                                    allMessages,
                                                                    newMessages,
                                                                    stopwatch);
                                            }
                                            else {
                                                yield processStreamingOutput(context,
                                                                             generatedOutput.get(),
                                                                             oldMessages,
                                                                             stats,
                                                                             allMessages,
                                                                             newMessages,
                                                                             stopwatch);
                                            }
                                        }
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
        }, agentSetup.getExecutorService());
    }


    private static void addOutputExtractionTool(
            HashMap<String, ExecutableTool> toolsForExecution,
            ObjectNode schema,
            UnaryOperator<String> outputGenerator,
            AtomicReference<String> generatedOutput) {
        toolsForExecution.put(Agent.OUTPUT_GENERATOR_ID,
                              new ExternalTool(ToolDefinition.builder()
                                                       .id(Agent.OUTPUT_GENERATOR_ID)
                                                       .name(Agent.OUTPUT_GENERATOR_ID)
                                                       .description("Generates output to be used by user")
                                                       .contextAware(true)
                                                       .strictSchema(true)
                                                       .build(),
                                               schema,
                                               (runContext, toolCallId, args) -> {
                                                   try {
                                                       final var output = outputGenerator.apply(args);
                                                       if (!Strings.isNullOrEmpty(output)) {
                                                           generatedOutput.set(output);
                                                       }
                                                       return new ExternalTool.ExternalToolResponse(
                                                               output,
                                                               ErrorType.SUCCESS);
                                                   }
                                                   catch (Throwable t) {
                                                       final var rootCause = AgentUtils.rootCause(t);
                                                       log.error("Error generating output: " + rootCause.getMessage(),
                                                                 t);
                                                       return new ExternalTool.ExternalToolResponse(
                                                               "Error running tool: " + rootCause.getMessage(),
                                                               ErrorType.TOOL_CALL_PERMANENT_FAILURE);
                                                   }
                                               }));
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
        final var id = !Strings.isNullOrEmpty(call.getId())
                       ? call.getId()
                       : existing.getId();

        final var function = Objects.requireNonNullElseGet(existing.getFunction(), FunctionCall::new);
        final var name = call.getFunction().getName();
        if (!Strings.isNullOrEmpty(name)) {
            function.setName(existingString(function.getName()) + name);
        }
        final var arguments = call.getFunction().getArguments();
        if (!Strings.isNullOrEmpty(arguments)) {
            function.setArguments(existingString(function.getArguments()) + arguments);
        }
        return new io.github.sashirestela.openai.common.tool.ToolCall(
                existing.getIndex(),
                id,
                existing.getType(),
                function);
    }

    private static String existingString(String input) {
        return Strings.isNullOrEmpty(input) ? "" : input;
    }

    private ModelOutput processOutput(
            ModelRunContext context,
            String content,
            List<AgentMessage> oldMessages,
            ModelUsageStats stats,
            ArrayList<AgentMessage> allMessages,
            ArrayList<AgentMessage> newMessages,
            Stopwatch stopwatch) {
        if (!Strings.isNullOrEmpty(content)) {
            final var newMessage = new StructuredOutput(content);
            allMessages.add(newMessage);
            newMessages.add(newMessage);
            raiseMessageReceivedEvent(context, newMessage, stopwatch);
            raiseOutputGeneratedEvent(context, content, stopwatch);
            try {
                return ModelOutput.success(mapper.readTree(content),
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

    /**
     * In case of streaming output, the text content is directly passed as a response as a text node in the model
     * output.
     */
    private ModelOutput processStreamingOutput(
            ModelRunContext context,
            String content,
            List<AgentMessage> oldMessages,
            ModelUsageStats stats,
            ArrayList<AgentMessage> allMessages,
            ArrayList<AgentMessage> newMessages,
            Stopwatch stopwatch) {
        //Model has sent all response
        if (!Strings.isNullOrEmpty(content)) {
            final var newMessage = new Text(content); //Always text output
            allMessages.add(newMessage);
            newMessages.add(newMessage);
            raiseMessageReceivedEvent(context, newMessage, stopwatch);
            raiseOutputGeneratedEvent(context, content, stopwatch);
            try {
                return ModelOutput.success(mapper.createObjectNode().textNode(content),
                                           newMessages,
                                           allMessages,
                                           stats);
            }
            catch (Exception e) {
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
        if (!tools.isEmpty()) {
            builder.parallelToolCalls(Objects.requireNonNullElse(modelSettings.getParallelToolCalls(), true));
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
     * Handle tool calls from the model
     */
    private static <R, T, A extends Agent<R, T, A>> void handleToolCalls(
            ModelRunContext context,
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner,
            List<io.github.sashirestela.openai.common.tool.ToolCall> toolCalls,
            AgentMessages agentMessages,
            ModelUsageStats stats,
            Stopwatch stopwatch) {
        handleToolCalls(context.getAgentName(),
                        context.getRunId(),
                        context.getSessionId(),
                        context.getUserId(),
                        context.getAgentSetup(),
                        tools,
                        toolRunner,
                        toolCalls,
                        agentMessages,
                        stats,
                        stopwatch);
    }

    @SuppressWarnings("java:S107")
    private static <R, T, A extends Agent<R, T, A>> void handleToolCalls(
            String agentName,
            String runId,
            String sessionId,
            String userId,
            AgentSetup agentSetup,
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner,
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
                            raiseMessageReceivedEvent(
                                    agentName,
                                    runId,
                                    sessionId,
                                    userId,
                                    agentSetup,
                                    toolCallMessage,
                                    stopwatch);
                            final var toolCallResponse = callTool(tools, toolRunner, toolCallMessage);
                            return Pair.of(toolCallMessage, toolCallResponse);
                        }, agentSetup.getExecutorService()))
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
                    agentMessages.getAllMessages().add(toolCallResponse);
                    agentMessages.getNewMessages().add(toolCallResponse);
                    stats.incrementToolCallsForRun();
                });
    }

    private static ToolCallResponse callTool(
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner,
            ToolCall toolCallMessage) {
        return null != toolRunner
               ? toolRunner.runTool(tools, toolCallMessage)
               : new ToolCallResponse(toolCallMessage.getToolCallId(),
                                      toolCallMessage.getToolName(),
                                      ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                      "Tool runner not provided for tool call %s[%s]".formatted(toolCallMessage.getToolCallId(),
                                                                                                toolCallMessage.getToolName()),
                                      LocalDateTime.now());
    }

    /**
     * Provides openai compliant schema for a map having the different outputs as entries.
     * Key of the map is {@link ModelOutputDefinition#getName()} and value schema is set to
     * {@link ModelOutputDefinition#getSchema()}
     *
     * @param outputDefinitions List of output definitions from the agent and it's extensions
     * @return OpenAI compliant schem
     */
    private ObjectNode compliantSchema(
            Collection<ModelOutputDefinition> outputDefinitions) {
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        final var fields = mapper.createArrayNode();
        schema.set("required", fields);
        final var propertiesNode = mapper.createObjectNode();
        schema.set("properties", propertiesNode);

        outputDefinitions.forEach(outputDefinition -> {
            fields.add(outputDefinition.getName());
            propertiesNode.set(outputDefinition.getName(), outputDefinition.getSchema());
        });
        return schema;
    }


    /**
     * Converts sentinel messages to OpenAI message format.
     *
     * @param agentMessages List of sentinel messages to convert
     * @return List of OpenAI messages
     */
    private List<ChatMessage> convertToOpenAIMessages(List<AgentMessage> agentMessages) {
        return Objects.requireNonNullElseGet(agentMessages, List::<AgentMessage>of)
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

    private ToolChoiceOption computeToolChoice(OutputGenerationMode outputGenerationMode) {
        return switch (this.modelOptions.getToolChoice()) {
            case REQUIRED -> switch (outputGenerationMode) {
                case TOOL_BASED -> ToolChoiceOption.REQUIRED;
                case STRUCTURED_OUTPUT -> ToolChoiceOption.AUTO;
            };
            case AUTO -> ToolChoiceOption.AUTO;
        };
    }

}
