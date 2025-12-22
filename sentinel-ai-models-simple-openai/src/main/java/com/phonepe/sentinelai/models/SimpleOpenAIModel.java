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
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategyResponse;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessContext;
import com.phonepe.sentinelai.core.model.*;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.ParameterMapper;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.Pair;
import io.github.sashirestela.cleverclient.support.CleverClientException;
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
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.phonepe.sentinelai.core.utils.AgentUtils.safeGetInt;
import static com.phonepe.sentinelai.core.utils.EventUtils.*;
import static com.phonepe.sentinelai.models.utils.OpenAIMessageUtils.convertIndividualMessageToOpenIDFormat;
import static com.phonepe.sentinelai.models.utils.OpenAIMessageUtils.convertToOpenAIMessages;

/**
 * Model implementation based on SimpleOpenAI client.
 * <p>
 * Please check <a href="https://github.com/sashirestela/simple-openai">Simple OpenAI Repo</a>
 * for details of client usage
 */
@Slf4j
@Getter
public class SimpleOpenAIModel<M extends ChatCompletionServices> implements Model {

    @Builder
    @Value
    private static class AgentMessages {
        List<ChatMessage> openAiMessages;
        List<AgentMessage> allMessages;
        List<AgentMessage> newMessages;
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
    private final ChatCompletionServiceFactory openAIProviderFactory;
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
        this(modelName,
             new DefaultChatCompletionServiceFactory(openAIProvider),
             mapper,
             modelOptions);
    }

    public SimpleOpenAIModel(
            final String modelName,
            @NonNull final ChatCompletionServiceFactory openAIProviderFactory,
            final ObjectMapper mapper,
            final SimpleOpenAIModelOptions modelOptions) {
        this.modelName = modelName;
        this.openAIProviderFactory = openAIProviderFactory;
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
            ToolRunner toolRunner,
            EarlyTerminationStrategy earlyTerminationStrategy,
            List<AgentMessagesPreProcessor> messagesPreProcessors) {
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
                final var stopwatch = Stopwatch.createStarted();

                ModelOutput error = executeMessagesPreProcessors(context,
                                                                 oldMessages,
                                                                 messagesPreProcessors,
                                                                 stats,
                                                                 allMessages,
                                                                 newMessages,
                                                                 openAiMessages);

                if (error != null) {
                    output = error;
                    break;
                }

                generatedOutput.set(null);
                final var request = toChatRequest(openAiMessages, modelSettings, toolsForExecution, outputGenerationMode, schema);

                raiseMessageSentEvent(context, oldMessages);
                stats.incrementRequestsForRun();

                logModelRequest(request);
                Chat completionResponse;

                try {
                    completionResponse = openAIProviderFactory.get(modelName)
                            .chatCompletions()
                            .create(request)
                            .join();
                }
                catch (Exception e) {
                    return errorToModelOutput(context, e, newMessages, allMessages)
                            .orElseThrow(() -> new RuntimeException(e));
                }
                logModelResponse(completionResponse);
                mergeUsage(stats, completionResponse.getUsage());
                mergeUsage(context.getModelUsageStats(), completionResponse.getUsage());
                final var response = extractResponse(completionResponse);
                if (null == response) {
                    return ModelOutput.error(oldMessages, stats, SentinelError.error(ErrorType.NO_RESPONSE));
                }

                output = handleModelResponse(context,
                                             oldMessages,
                                             toolRunner,
                                             response,
                                             stats,
                                             toolsForExecution,
                                             stopwatch,
                                             generatedOutput,
                                             openAiMessages,
                                             allMessages,
                                             newMessages);

                if (shouldLoop(output)) {
                    output = evaluateRunTerminationStrategy(context,
                                                            earlyTerminationStrategy,
                                                            modelSettings,
                                                            output,
                                                            stats);
                }
            } while (shouldLoop(output));
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
            EarlyTerminationStrategy earlyTerminationStrategy,
            Consumer<byte[]> streamHandler,
            List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
        return streamImpl(context,
                          outputDefinitions,
                          oldMessages,
                          tools,
                          toolRunner,
                          earlyTerminationStrategy,
                          streamHandler,
                          Agent.StreamProcessingMode.TYPED,
                          agentMessagesPreProcessors);
    }

    @Override
    public CompletableFuture<ModelOutput> streamText(
            ModelRunContext context,
            List<AgentMessage> oldMessages,
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner,
            EarlyTerminationStrategy earlyTerminationStrategy,
            Consumer<byte[]> streamHandler,
            List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
        return streamImpl(context,
                          List.of(),
                          oldMessages,
                          tools,
                          toolRunner,
                          earlyTerminationStrategy,
                          streamHandler,
                          Agent.StreamProcessingMode.TEXT,
                          agentMessagesPreProcessors);
    }

    @SuppressWarnings({"java:S107", "java:S3776"})
    private CompletableFuture<ModelOutput> streamImpl(
            ModelRunContext context,
            Collection<ModelOutputDefinition> outputDefinitions,
            List<AgentMessage> oldMessages,
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner,
            EarlyTerminationStrategy earlyTerminationStrategy,
            Consumer<byte[]> streamHandler,
            Agent.StreamProcessingMode streamProcessingMode,
            List<AgentMessagesPreProcessor> messagesPreProcessors) {
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
                ModelOutput error = executeMessagesPreProcessors(context,
                                                                 oldMessages,
                                                                 messagesPreProcessors,
                                                                 stats,
                                                                 allMessages,
                                                                 newMessages,
                                                                 openAiMessages);
                if (error != null) {
                    output = error;
                    break;
                }

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
                addToolChoice(toolsForExecution, builder, outputGenerationMode);
                final var stopwatch = Stopwatch.createStarted();
                stats.incrementRequestsForRun();

                final var request = builder.build();
                logModelRequest(request);
                Stream<Chat> completionResponseStream;

                try {
                    completionResponseStream = openAIProviderFactory.get(modelName)
                            .chatCompletions()
                            .createStream(request)
                            .join();
                }
                catch (Exception e) {
                    return errorToModelOutput(context, e, newMessages, allMessages)
                            .orElseThrow(() -> new RuntimeException(e));
                }
                //We use the following to merge the pieces of response we get from stream into final output
                final var responseData = new StringBuilder();
                //We use the following to cobble together the fragment of tool call objects we get from the stream
                final var toolCallData = new HashMap<Integer, io.github.sashirestela.openai.common.tool.ToolCall>();

                final var outputs = completionResponseStream
                        .map(completionResponse -> {
                            logModelResponse(completionResponse);
                            mergeUsage(stats, completionResponse.getUsage());
                            mergeUsage(context.getModelUsageStats(), completionResponse.getUsage());
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
                if (shouldLoop(output)) {
                    output = evaluateRunTerminationStrategy(context,
                                                            earlyTerminationStrategy,
                                                            modelSettings,
                                                            output,
                                                            stats);
                }
            } while (shouldLoop(output));
            return output;
        }, agentSetup.getExecutorService());
    }

    private static Optional<ModelOutput> errorToModelOutput(
            final ModelRunContext context,
            final Throwable error,
            final List<AgentMessage> newMessages,
            final List<AgentMessage> allMessages) {
        final var rootCause = AgentUtils.rootCause(error);
        log.error("Error calling model: %s -> %s".formatted(
                          rootCause.getClass().getSimpleName(), rootCause.getMessage()),
                  error);
        // Looks like OkHttp sends out a variety of IOExceptions for network issues
        if (ClassUtils.isAssignable(rootCause.getClass(), IOException.class)) {
            return errorToModelOutput(context,
                                      newMessages,
                                      allMessages,
                                      ErrorType.MODEL_CALL_COMMUNICATION_ERROR,
                                      rootCause.getMessage());
        }
        // Now that we have all network errors covered, we check for different status codes etc
        if (rootCause instanceof CleverClientException cleverClientException) {
            return cleverClientException.responseInfo()
                    .flatMap(responseInfo -> {
                        final var message = Objects.requireNonNullElse(responseInfo.getData(),
                                                                       cleverClientException.getMessage());
                        return switch (responseInfo.getStatusCode()) {
                            case 429 -> errorToModelOutput(context,
                                                           newMessages,
                                                           allMessages,
                                                           ErrorType.MODEL_CALL_RATE_LIMIT_EXCEEDED,
                                                           message);
                            default -> errorToModelOutput(context,
                                                          newMessages,
                                                          allMessages,
                                                          ErrorType.MODEL_CALL_HTTP_FAILURE,
                                                          "Received HTTP error:  [%d] %s".formatted(
                                                                  responseInfo.getStatusCode(),
                                                                  message));
                        };
                    })
                    .or(() -> errorToModelOutput(context,
                                                 newMessages,
                                                 allMessages,
                                                 ErrorType.GENERIC_MODEL_CALL_FAILURE,
                                                 cleverClientException.getMessage()));
        }
        return Optional.empty();
    }

    private static Optional<ModelOutput> errorToModelOutput(
            final ModelRunContext context,
            final List<AgentMessage> newMessages,
            final List<AgentMessage> allMessages,
            final ErrorType errorType,
            final String message) {
        return Optional.of(ModelOutput.error(
                newMessages,
                allMessages,
                context.getModelUsageStats(),
                SentinelError.error(errorType, message)));
    }

    @SuppressWarnings("java:S107")
    private Optional<ModelOutput> runTools(
            List<io.github.sashirestela.openai.common.tool.ToolCall> receivedCalls,
            ModelRunContext context,
            Map<String, ExecutableTool> toolsForExecution,
            ToolRunner toolRunner,
            ModelUsageStats stats,
            Stopwatch stopwatch,
            AtomicReference<String> generatesOutput,
            ArrayList<ChatMessage> openAiMessages,
            ArrayList<AgentMessage> allMessages,
            ArrayList<AgentMessage> newMessages,
            List<AgentMessage> oldMessages) {
        final var toolCalls = Objects.requireNonNullElseGet(receivedCalls,
                                                            List::<io.github.sashirestela.openai.common.tool.ToolCall>of);

        if (!toolCalls.isEmpty()) {
            handleToolCalls(context,
                            toolsForExecution,
                            toolRunner,
                            toolCalls,
                            new AgentMessages(openAiMessages, allMessages, newMessages),
                            stats,
                            stopwatch);
            return Optional.ofNullable(generatesOutput.get())
                    .map(data -> processOutput(
                            context,
                            data,
                            oldMessages,
                            stats,
                            allMessages,
                            newMessages,
                            stopwatch));

        }
        return Optional.empty();
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
                                                       .terminal(true)
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


    private static boolean shouldLoop(final ModelOutput output) {
        return output == null || (output.getData() == null && output.getError() == null);
    }

    private static boolean isEarlyTermination(final EarlyTerminationStrategyResponse strategyResponse) {
        return Optional.ofNullable(strategyResponse)
                .map(response -> response.getResponseType() == EarlyTerminationStrategyResponse.ResponseType.TERMINATE)
                .orElse(false);
    }


    private static ModelOutput evaluateRunTerminationStrategy(
            ModelRunContext context,
            EarlyTerminationStrategy earlyTerminationStrategy,
            ModelSettings modelSettings,
            ModelOutput output,
            ModelUsageStats stats) {
        final var strategyResponse = earlyTerminationStrategy.evaluate(modelSettings, context, output);
        if (isEarlyTermination(strategyResponse)) {
            output = ModelOutput.error(
                    Optional.ofNullable(output)
                            .map(ModelOutput::getAllMessages)
                            .orElse(List.of()),
                    stats,
                    new SentinelError(strategyResponse.getErrorType(), strategyResponse.getReason()));
        }
        return output;
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

    private ChatRequest.ChatRequestBuilder createChatRequestBuilder(List<ChatMessage> openAiMessages) {
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
        if (null != modelSettings.getReasoning()) {
            builder.reasoningEffort(translateReasoningEffort(modelSettings));
        }
    }

    /**
     * Translate reasoning effort from model settings to ChatRequest enum.
     * @param modelSettings Model settings
     * @return Translated reasoning effort
     */
    private static ChatRequest.ReasoningEffort translateReasoningEffort(ModelSettings modelSettings) {
        return switch (modelSettings.getReasoning()) {
            case LOW -> ChatRequest.ReasoningEffort.LOW;
            case MEDIUM -> ChatRequest.ReasoningEffort.MEDIUM;
            case HIGH -> ChatRequest.ReasoningEffort.HIGH;
            case MINIMAL -> ChatRequest.ReasoningEffort.MINIMAL;
        };
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


    private void addToolChoice(
            Map<String, ExecutableTool> toolsForExecution,
            ChatRequest.ChatRequestBuilder builder,
            OutputGenerationMode outputGenerationMode) {
        // Looks like some models do not like the tool_choice being sent if there are no tools specified
        if (!toolsForExecution.isEmpty()) {
            builder.toolChoice(computeToolChoice(outputGenerationMode));
        }
    }

    private ToolChoiceOption computeToolChoice(OutputGenerationMode outputGenerationMode) {
        return switch (outputGenerationMode) {
            case TOOL_BASED -> ToolChoiceOption.REQUIRED;
            case STRUCTURED_OUTPUT -> switch (this.modelOptions.getToolChoice()) {
                case REQUIRED -> {
                    log.warn("Model is configured for STRUCTURED_OUTPUT generation mode, " +
                                     "but tool choice is set to REQUIRED. This might lead to infinite tool-call loops");
                    yield ToolChoiceOption.REQUIRED;
                }
                case AUTO -> ToolChoiceOption.AUTO;
            };
        };
    }

    private ModelOutput executeMessagesPreProcessors(final ModelRunContext context,
                                                     final List<AgentMessage> oldMessages,
                                                     final List<AgentMessagesPreProcessor> messagesPreProcessors,
                                                     final ModelUsageStats stats,
                                                     final List<AgentMessage> allMessages,
                                                     final List<AgentMessage> newMessages,
                                                     final List<ChatMessage> openAiMessages) {
        try {
            executeMessagesPreProcessors(context, messagesPreProcessors, stats, allMessages, newMessages, openAiMessages);
        } catch (Exception e) {
            log.error("Error running pre-processors ", e);
            return ModelOutput.error(oldMessages, stats, SentinelError.error(ErrorType.PREPROCESSOR_RUN_FAILURE));
        }
        return null;
    }

    private synchronized void executeMessagesPreProcessors(final ModelRunContext context,
                                                           final List<AgentMessagesPreProcessor> messagesPreProcessors,
                                                           final ModelUsageStats stats,
                                                           final List<AgentMessage> allMessages,
                                                           final List<AgentMessage> newMessages,
                                                           final List<ChatMessage> openAiMessages) {
        final var processedAgentMessages = executeMessagesPreProcessors(messagesPreProcessors, stats, context, allMessages);
        processedAgentMessages.ifPresent(processedMessages -> {
            allMessages.clear();
            newMessages.clear();
            openAiMessages.clear();

            allMessages.addAll(processedMessages.allMessages);
            newMessages.addAll(processedMessages.newMessages);
            openAiMessages.addAll(processedMessages.openAiMessages);
        });
    }

    /**
     *
     * Executes the given set of pre-processors.
     * The processors are executed as a chain/pipeline where the valid output of one processor is passed as an input
     * for the next processor in the chain.
     *
     */
    private Optional<AgentMessages> executeMessagesPreProcessors(final List<AgentMessagesPreProcessor> messagesPreProcessors,
                                                                 final ModelUsageStats statsForRun,
                                                                 final ModelRunContext context,
                                                                 final List<AgentMessage> allMessages) {
        if (messagesPreProcessors == null || messagesPreProcessors.isEmpty()) {
            log.trace("No agent messages pre-processors found");
            return Optional.empty();
        }

        var transformedMessages = List.copyOf(allMessages);

        for (var processor : messagesPreProcessors) {
            final var response = processor.process(AgentMessagesPreProcessContext.builder()
                    .statsForRun(statsForRun)
                    .allMessages(transformedMessages)
                    .modelRunContext(context)
                    .build());

            // check if processor transformed the messages
            if (response != null
                    && response.getTransformedMessages() != null) {
                transformedMessages = List.copyOf(response.getTransformedMessages());
            }
        }

        if (transformedMessages.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(AgentMessages.builder()
                        .allMessages(transformedMessages)
                        .newMessages(List.of())
                        .openAiMessages(convertToOpenAIMessages(transformedMessages))
                .build());
    }

    private ChatRequest toChatRequest(final List<ChatMessage> openAiMessages,
                                      final ModelSettings modelSettings,
                                      final HashMap<String, ExecutableTool> toolsForExecution,
                                      final OutputGenerationMode outputGenerationMode,
                                      final ObjectNode schema) {
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
        addToolChoice(toolsForExecution, builder, outputGenerationMode);
        return builder.build();
    }

    @Nullable
    private ModelOutput handleModelResponse(final ModelRunContext context,
                                            final List<AgentMessage> oldMessages,
                                            final ToolRunner toolRunner,
                                            final Chat.Choice response,
                                            final ModelUsageStats stats,
                                            final HashMap<String, ExecutableTool> toolsForExecution,
                                            final Stopwatch stopwatch,
                                            final AtomicReference<String> generatedOutput,
                                            final ArrayList<ChatMessage> openAiMessages,
                                            final ArrayList<AgentMessage> allMessages,
                                            final ArrayList<AgentMessage> newMessages) {
        final var message = response.getMessage();
        return switch (response.getFinishReason()) {
            case FinishReasons.STOP -> {
                final var refusal = message.getRefusal();
                if (!Strings.isNullOrEmpty(refusal)) {
                    yield ModelOutput.error(oldMessages,
                            stats,
                            SentinelError.error(ErrorType.REFUSED, refusal));
                }
                yield runTools(message.getToolCalls(),
                        context,
                        toolsForExecution,
                        toolRunner,
                        stats,
                        stopwatch,
                        generatedOutput,
                        openAiMessages,
                        allMessages,
                        newMessages,
                        oldMessages)
                        .orElseGet(() -> processOutput(context,
                                message.getContent(),
                                oldMessages,
                                stats,
                                allMessages,
                                newMessages,
                                stopwatch));
            }
            case FinishReasons.FUNCTION_CALL, FinishReasons.TOOL_CALLS -> runTools(message.getToolCalls(),
                    context,
                    toolsForExecution,
                    toolRunner,
                    stats,
                    stopwatch,
                    generatedOutput,
                    openAiMessages,
                    allMessages,
                    newMessages,
                    oldMessages)
                    .orElse(null);
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
    }

}
