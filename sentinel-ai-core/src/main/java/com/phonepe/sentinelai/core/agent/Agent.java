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

package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.primitives.Primitives;

import io.appform.signals.signals.ConsumingFireForgetSignal;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errorhandling.DefaultErrorHandler;
import com.phonepe.sentinelai.core.errorhandling.ErrorResponseHandler;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.outputvalidation.DefaultOutputValidator;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidationResults;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidator;
import com.phonepe.sentinelai.core.outputvalidation.ValidationErrorFixPrompt;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.InternalTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.tools.ToolRunApprovalSeeker;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.EventUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;

import dev.failsafe.Failsafe;
import dev.failsafe.RetryPolicy;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.phonepe.sentinelai.core.utils.JsonUtils.schema;
import static java.util.stream.Collectors.toMap;

/**
 * Base class for all agents. Derive this to create own agents.
 *
 * @param <R> Request type
 * @param <T> Response type
 * @param <A> Agent type reference of the subclass using this as base class
 */
@Slf4j
public abstract class Agent<R, T, A extends Agent<R, T, A>> {

    public static final String OUTPUT_GENERATOR_ID = "__output_generator__";

    public static final String OUTPUT_VARIABLE_NAME = "output";

    public enum StreamProcessingMode {
        TYPED,
        TEXT
    }

    @Value
    public static class ProcessingCompletedData<R, T, A extends Agent<R, T, A>> {
        A agent;
        AgentSetup agentSetup;
        AgentRunContext<R> context;
        AgentInput<R> input;
        AgentOutput<?> output;
        ProcessingMode processingMode;
    }

    private record ModelOutputProcessingContext<R>(
            AgentRunContext<R> context,
            AgentSetup agentSetup,
            List<AgentMessage> messages
    ) {
    }

    private final Class<T> outputType;
    private final String systemPrompt;
    @Getter
    private final AgentSetup setup;
    private final List<AgentExtension<R, T, A>> extensions;
    private final ToolRunApprovalSeeker<R, T, A> toolRunApprovalSeeker;
    private final OutputValidator<R, T> outputValidator;
    private final ErrorResponseHandler<R> errorHandler;
    private final EarlyTerminationStrategy earlyTerminationStrategy;

    private final Map<String, ExecutableTool> knownTools = new ConcurrentHashMap<>();
    private final XmlMapper xmlMapper = new XmlMapper();
    private final ConsumingFireForgetSignal<ProcessingCompletedData<R, T, A>> requestCompleted = new ConsumingFireForgetSignal<>();
    private final List<AgentMessagesPreProcessor> agentMessagesPreProcessors = new CopyOnWriteArrayList<>();

    @SuppressWarnings("unchecked")
    private final A self = (A) this;

    protected Agent(@NonNull final Class<T> outputType,
                    @NonNull final String systemPrompt,
                    @NonNull final AgentSetup setup,
                    final List<AgentExtension<R, T, A>> extensions,
                    final Map<String, ExecutableTool> knownTools) {
        this(outputType,
             systemPrompt,
             setup,
             extensions,
             knownTools,
             new ApproveAllToolRuns<>(),
             new DefaultOutputValidator<>(),
             new DefaultErrorHandler<>(),
             new NeverTerminateEarlyStrategy());
    }

    @SneakyThrows
    @SuppressWarnings("java:S107")
    protected Agent(@NonNull final Class<T> outputType,
                    @NonNull final String systemPrompt,
                    @NonNull final AgentSetup setup,
                    final List<AgentExtension<R, T, A>> extensions,
                    final Map<String, ExecutableTool> knownTools,
                    final ToolRunApprovalSeeker<R, T, A> toolRunApprovalSeeker,
                    final OutputValidator<R, T> outputValidator,
                    final ErrorResponseHandler<R> errorHandler,
                    final EarlyTerminationStrategy earlyTerminationStrategy) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(systemPrompt),
                                    "Please provide a valid system prompt");

        this.outputType = outputType;
        this.systemPrompt = systemPrompt;
        this.setup = setup.withExecutorService(Objects.requireNonNullElseGet(
                                                                             setup.getExecutorService(),
                                                                             Executors::newCachedThreadPool))
                .withEventBus(Objects.requireNonNullElseGet(setup.getEventBus(),
                                                            EventBus::new));
        this.extensions = Objects.requireNonNullElseGet(extensions, List::of);
        this.toolRunApprovalSeeker = Objects.requireNonNullElseGet(
                                                                   toolRunApprovalSeeker,
                                                                   ApproveAllToolRuns::new);
        this.outputValidator = Objects.requireNonNullElseGet(outputValidator,
                                                             DefaultOutputValidator::new);
        this.errorHandler = Objects.requireNonNullElseGet(errorHandler,
                                                          DefaultErrorHandler::new);
        this.earlyTerminationStrategy = Objects.requireNonNullElseGet(
                                                                      earlyTerminationStrategy,
                                                                      NeverTerminateEarlyStrategy::new);

        xmlMapper.registerModule(new JavaTimeModule());
        xmlMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_1_1, true);
        xmlMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        xmlMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);
        registerTools(ToolUtils.readTools(this));
        registerTools(knownTools);
        this.extensions.forEach(extension -> {
            registerToolbox(extension);
            extension.onExtensionRegistrationCompleted(self);
        });
    }

    public abstract String name();


    public ConsumingFireForgetSignal<ProcessingCompletedData<R, T, A>> onRequestCompleted() {
        return requestCompleted;
    }

    /**
     * Register toolboxes with the agent
     *
     * @param toolbox List of toolboxes
     * @return this
     */
    public A registerToolboxes(final List<ToolBox> toolbox) {
        Objects.requireNonNullElseGet(toolbox, List::<ToolBox>of)
                .forEach(this::registerToolbox);
        return self;
    }

    /**
     * Register a toolbox with the agent
     *
     * @param toolBox Toolbox to register
     * @return this
     */
    public A registerToolbox(ToolBox toolBox) {
        registerTools(toolBox.tools());
        toolBox.onToolBoxRegistrationCompleted(self);
        return self;
    }

    /**
     * Register tools with the agent directly
     *
     * @param tools List of callable tools
     * @return this
     */
    public A registerTools(List<ExecutableTool> tools) {
        return registerTools(Objects.requireNonNullElseGet(tools,
                                                           List::<InternalTool>of)
                .stream()
                .collect(toMap(tool -> tool.getToolDefinition().getId(),
                               Function.identity())));
    }

    /**
     * Register tools with the agent directly
     *
     * @param callableTools Map of callable tools
     * @return this
     */
    public A registerTools(Map<String, ExecutableTool> callableTools) {
        final var tools = new HashMap<>(Objects.requireNonNullElseGet(
                                                                      callableTools,
                                                                      Map::of));
        if (!tools.isEmpty()) {
            log.info("Discovered tools: {}", tools.keySet());
        }
        else {
            log.debug("No tools registered");
        }
        this.knownTools.putAll(tools);
        return self;
    }

    /**
     * Register agent message pre-processor. This is thread safe and can be called at runtime.
     *
     * @param agentMessagesPreProcessor processor instance
     * @return this
     */
    public A registerAgentMessagesPreProcessor(AgentMessagesPreProcessor agentMessagesPreProcessor) {
        this.agentMessagesPreProcessors.add(agentMessagesPreProcessor);
        log.debug("Registering messages pre-processor: {} for agent: {}",
                  agentMessagesPreProcessor.getClass().getSimpleName(),
                  name());
        return self;
    }

    /**
     * Register list of agent message pre-processor. This is thread safe and can be called at runtime.
     *
     * @param agentMessagesPreProcessors processor instances
     * @return this
     */
    public A registerAgentMessagesPreProcessors(List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
        agentMessagesPreProcessors.forEach(
                                           self::registerAgentMessagesPreProcessor);
        return self;
    }

    public final AgentOutput<T> execute(final AgentInput<R> request) {
        return executeAsync(request).join();
    }

    public Map<String, ExecutableTool> tools() {
        return Map.copyOf(knownTools);
    }

    /**
     * Execute the agent synchronously.
     *
     * @param input Input to the agent
     * @return The response from the agent
     */
    public final CompletableFuture<AgentOutput<T>> executeAsync(@NonNull AgentInput<R> input) {
        final var mergedAgentSetup = AgentUtils.mergeAgentSetup(input
                .getAgentSetup(), this.setup);
        final var messages = new ArrayList<>(Objects.requireNonNullElse(input
                .getOldMessages(), List.of()));
        final var requestMetadata = Objects.requireNonNullElseGet(input
                .getRequestMetadata(),
                                                                  AgentRequestMetadata::new);
        final var runId = AgentUtils.createIfNotNull(requestMetadata,
                                                     AgentRequestMetadata::getRunId,
                                                     () -> UUID.randomUUID()
                                                             .toString());
        final var facts = input.getFacts();
        final var inputRequest = input.getRequest();
        final var modelUsageStats = new ModelUsageStats();
        final var context = new AgentRunContext<>(runId,
                                                  inputRequest,
                                                  requestMetadata,
                                                  mergedAgentSetup,
                                                  messages,
                                                  modelUsageStats,
                                                  ProcessingMode.DIRECT);
        EventUtils.raiseInputReceivedEvent(name(),
                                           context,
                                           inputRequest,
                                           mergedAgentSetup);
        var finalSystemPrompt = "";
        try {
            finalSystemPrompt = systemPrompt(context, facts);
        }
        catch (JsonProcessingException e) {
            log.error("Error serializing system prompt", e);
            return CompletableFuture.completedFuture(AgentOutput.error(messages,
                                                                       context.getModelUsageStats(),
                                                                       SentinelError
                                                                               .error(ErrorType.SERIALIZATION_ERROR,
                                                                                      e)));
        }
        messages.add(new com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt(AgentUtils
                .sessionId(context), runId, finalSystemPrompt, false, null));
        messages.addAll(extensionMessages(inputRequest, context));
        messages.add(new UserPrompt(AgentUtils.sessionId(context),
                                    context.getRunId(),
                                    toXmlContent(inputRequest),
                                    LocalDateTime.now()));
        final var processingMode = ProcessingMode.DIRECT;
        final var modelRunContext = new ModelRunContext(name(),
                                                        runId,
                                                        AgentUtils.sessionId(
                                                                             context),
                                                        AgentUtils.userId(
                                                                          context),
                                                        mergedAgentSetup,
                                                        modelUsageStats,
                                                        processingMode);
        final var outputDefinitions = populateOutputDefinitions(processingMode);
        final var retryPolicy = Agent.<T>buildRetryPolicy(mergedAgentSetup);
        return Failsafe.with(List.of(retryPolicy))
                .with(mergedAgentSetup.getExecutorService())
                .getAsync(executionContext -> {
                    log.debug("Model sync call attempt: {}",
                              executionContext.getAttemptCount());
                    final var modelOutput = makeModelCall(mergedAgentSetup,
                                                          modelRunContext,
                                                          outputDefinitions,
                                                          messages,
                                                          context);
                    return errorHandler.handle(context,
                                               processModelOutput(new ModelOutputProcessingContext<>(context,
                                                                                                     mergedAgentSetup,
                                                                                                     messages),
                                                                  modelOutput));
                })
                .thenApply(response -> {
                    if (null != response
                            .getUsage() && requestMetadata != null && requestMetadata
                                    .getUsageStats() != null) {
                        requestMetadata.getUsageStats()
                                .merge(response.getUsage());
                    }
                    requestCompleted.dispatch(new ProcessingCompletedData<>(
                                                                            self,
                                                                            mergedAgentSetup,
                                                                            context,
                                                                            input,
                                                                            response,
                                                                            ProcessingMode.DIRECT));
                    return response;
                });
    }

    /**
     * Streaming execution. This should be used for streaming applications like chat etc. When using reasoning models
     * that have a longer first byte to response, some gateways might time out, it is better to use this mode
     *
     * @param input         The input to the agent
     * @param streamHandler Client method for raw data stream
     * @return The response to be consumed by the client
     */
    public final CompletableFuture<AgentOutput<T>> executeAsyncStreaming(AgentInput<R> input,
                                                                         Consumer<byte[]> streamHandler) {
        return executeAsyncStreamingInternal(input,
                                             streamHandler,
                                             false,
                                             this::processModelOutput);

    }

    /**
     * Streaming execution. This should be used for text streaming applications like chat etc.
     *
     * @param input         The input to the agent
     * @param streamHandler Client method for raw text stream
     * @return The response to be consumed by the client
     */
    public final CompletableFuture<AgentOutput<String>> executeAsyncTextStreaming(AgentInput<R> input,
                                                                                  Consumer<byte[]> streamHandler) {
        return executeAsyncStreamingInternal(input,
                                             streamHandler,
                                             true,
                                             this::processTextStreamingOutput);
    }

    /**
     * Schema for the agent output. Usually generated from the provided type, but can be overridden for more exotic
     * cases.
     *
     * @return A schema for the agent output
     */
    protected JsonNode outputSchema() {
        return schema(outputType);
    }

    /**
     * Translate the model output to the agent output type. This is used to convert the model output to the agent
     * output type.
     *
     * @param output     The model output
     * @param agentSetup The agent setup
     * @return The translated data
     * @throws JsonProcessingException If there is an error processing the JSON
     */
    protected T translateData(JsonNode output,
                              AgentSetup agentSetup) throws JsonProcessingException {
        return agentSetup.getMapper().treeToValue(output, outputType);
    }

    /**
     * Internal method to execute the agent asynchronously with streaming support.
     *
     * @param input           The input to the agent
     * @param streamHandler   The handler for the streamed output
     * @param isTextStreaming Whether the streaming is for text or structured output
     * @param outputProcessor Function to process the model output into agent output
     * @param <U>             The type of the agent output
     * @return A CompletableFuture that will complete with the agent output
     */
    private <U> CompletableFuture<AgentOutput<U>> executeAsyncStreamingInternal(AgentInput<R> input,
                                                                                Consumer<byte[]> streamHandler,
                                                                                boolean isTextStreaming,
                                                                                BiFunction<ModelOutputProcessingContext<R>, ModelOutput, AgentOutput<U>> outputProcessor) {
        final var mergedAgentSetup = AgentUtils.mergeAgentSetup(input
                .getAgentSetup(), this.setup);
        final var messages = new ArrayList<>(Objects.requireNonNullElse(input
                .getOldMessages(), List.of()));

        final var requestMetadata = Objects.requireNonNullElseGet(input
                .getRequestMetadata(),
                                                                  AgentRequestMetadata::new);
        final var runId = AgentUtils.createIfNotNull(requestMetadata,
                                                     AgentRequestMetadata::getRunId,
                                                     () -> UUID.randomUUID()
                                                             .toString());
        final var request = input.getRequest();
        final var facts = input.getFacts();
        final var processingMode = ProcessingMode.STREAMING;
        final var modelUsageStats = new ModelUsageStats();
        final var context = new AgentRunContext<>(runId,
                                                  request,
                                                  requestMetadata,
                                                  mergedAgentSetup,
                                                  messages,
                                                  modelUsageStats,
                                                  processingMode);
        EventUtils.raiseInputReceivedEvent(name(),
                                           context,
                                           request,
                                           mergedAgentSetup);
        var finalSystemPrompt = "";
        try {
            finalSystemPrompt = systemPrompt(context, facts);
        }
        catch (JsonProcessingException e) {
            log.error("Error serializing system prompt", e);
            return CompletableFuture.completedFuture(AgentOutput.error(messages,
                                                                       context.getModelUsageStats(),
                                                                       SentinelError
                                                                               .error(ErrorType.SERIALIZATION_ERROR,
                                                                                      e)));
        }
        messages.add(new com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt(AgentUtils
                .sessionId(context), runId, finalSystemPrompt, false, null));
        messages.addAll(extensionMessages(input.getRequest(), context));
        messages.add(new UserPrompt(AgentUtils.sessionId(context),
                                    context.getRunId(),
                                    toXmlContent(input),
                                    LocalDateTime.now()));
        final var modelRunContext = new ModelRunContext(name(),
                                                        runId,
                                                        AgentUtils.sessionId(
                                                                             context),
                                                        AgentUtils.userId(
                                                                          context),
                                                        mergedAgentSetup,
                                                        modelUsageStats,
                                                        processingMode);
        final var outputDefinitions = isTextStreaming ? List
                .<ModelOutputDefinition>of() : populateOutputDefinitions(
                                                                         processingMode);
        final var retryPolicy = Agent.<U>buildRetryPolicy(mergedAgentSetup);
        return Failsafe.with(List.of(retryPolicy))
                .with(mergedAgentSetup.getExecutorService())
                .getAsync(executionContext -> {
                    log.debug("Model streaming call attempt: {}",
                              executionContext.getAttemptCount());
                    final var modelOutput = makeAsyncModelCall(mergedAgentSetup,
                                                               modelRunContext,
                                                               outputDefinitions,
                                                               messages,
                                                               context,
                                                               earlyTerminationStrategy,
                                                               isTextStreaming,
                                                               streamHandler);
                    return errorHandler.handle(context,
                                               outputProcessor.apply(
                                                                     new ModelOutputProcessingContext<>(context,
                                                                                                        mergedAgentSetup,
                                                                                                        messages),
                                                                     modelOutput));
                })
                .thenApply(response -> {
                    if (null != response
                            .getUsage() && requestMetadata != null && requestMetadata
                                    .getUsageStats() != null) {
                        requestMetadata.getUsageStats()
                                .merge(response.getUsage());
                    }
                    requestCompleted.dispatch(new ProcessingCompletedData<>(
                                                                            self,
                                                                            mergedAgentSetup,
                                                                            context,
                                                                            input,
                                                                            response,
                                                                            processingMode));
                    return response;
                });
    }

    private static <U> RetryPolicy<AgentOutput<U>> buildRetryPolicy(AgentSetup mergedAgentSetup) {
        final var retrySetup = mergedAgentSetup.getRetrySetup();
        return RetryPolicy.<AgentOutput<U>>builder()
                .withMaxAttempts(retrySetup.getTotalAttempts())
                .withDelay(retrySetup.getDelayAfterFailedAttempt())
                .handleResultIf(response -> retrySetup.getRetriableErrorTypes()
                        .contains(response.getError().getErrorType()))
                .build();
    }

    private AgentOutput<T> processModelOutput(ModelOutputProcessingContext<R> processingContext,
                                              ModelOutput modelOutput) {
        final var context = processingContext.context();
        final var mergedAgentSetup = processingContext.agentSetup();
        final var messages = processingContext.messages();
        try {
            final var errorResponse = Agent.<T>handleErrorResponse(modelOutput)
                    .orElse(null);
            if (errorResponse != null) {
                mergeModelOutput(processingContext, modelOutput);
                return errorResponse;
            }
            //Creating an empty object here as we don't want to waste time doing null checks
            final var data = Objects.requireNonNullElseGet(modelOutput
                    .getData(), () -> setup.getMapper().createObjectNode());
            final var agentOutputData = data.get(OUTPUT_VARIABLE_NAME);

            if (JsonUtils.empty(agentOutputData)) {
                logEmptyData();
                return AgentOutput.error(modelOutput.getNewMessages(),
                                         modelOutput.getAllMessages(),
                                         modelOutput.getUsage(),
                                         SentinelError.error(
                                                             ErrorType.NO_RESPONSE));
            }
            final var translatedData = translateData(agentOutputData,
                                                     mergedAgentSetup);
            final var validationOutput = outputValidator.validate(context,
                                                                  translatedData);
            if (validationOutput.isSuccessful()) {
                processExtensionData(context, messages, data);
                return AgentOutput.success(translatedData,
                                           modelOutput.getNewMessages(),
                                           modelOutput.getAllMessages(),
                                           modelOutput.getUsage());
            }
            final var validationErrors = Joiner.on(",")
                    .join(validationOutput.getFailures()
                            .stream()
                            .map(OutputValidationResults.ValidationFailure::getMessage)
                            .toList());
            messages.add(new UserPrompt(AgentUtils.sessionId(context),
                                        context.getRunId(),
                                        toXmlContent(new ValidationErrorFixPrompt(validationErrors,
                                                                                  mergedAgentSetup
                                                                                          .getMapper()
                                                                                          .writeValueAsString(agentOutputData))),
                                        LocalDateTime.now()));
            return AgentOutput.error(modelOutput.getNewMessages(),
                                     modelOutput.getNewMessages(),
                                     modelOutput.getUsage(),
                                     SentinelError.error(
                                                         ErrorType.DATA_VALIDATION_FAILURE,
                                                         validationErrors));
        }
        catch (JsonProcessingException e) {
            log.error("Error converting model output to agent output. Error: {}",
                      AgentUtils.rootCause(e),
                      e);
            return AgentOutput.error(modelOutput.getNewMessages(),
                                     modelOutput.getAllMessages(),
                                     modelOutput.getUsage(),
                                     SentinelError.error(ErrorType.JSON_ERROR,
                                                         e));
        }
    }

    @SuppressWarnings("unused")
    private AgentOutput<String> processTextStreamingOutput(ModelOutputProcessingContext<R> processingContext,
                                                           ModelOutput modelOutput) {
        final var errorResponse = Agent.<String>handleErrorResponse(modelOutput)
                .orElse(null);
        if (errorResponse != null) {
            return errorResponse;
        }
        final var data = modelOutput.getData();
        if (JsonUtils.empty(data) || !data.isTextual()) {
            logEmptyData();
            return AgentOutput.error(modelOutput.getNewMessages(),
                                     modelOutput.getAllMessages(),
                                     modelOutput.getUsage(),
                                     SentinelError.error(ErrorType.NO_RESPONSE,
                                                         "Did not get output from model"));
        }
        return AgentOutput.success(data.asText(),
                                   modelOutput.getNewMessages(),
                                   modelOutput.getAllMessages(),
                                   modelOutput.getUsage());
    }

    private void processExtensionData(AgentRunContext<R> context,
                                      List<AgentMessage> messages,
                                      JsonNode data) {
        extensions.forEach(extension -> {
            final var outputDefinition = extension.outputSchema(
                                                                ProcessingMode.DIRECT);
            final var outputName = outputDefinition.map(
                                                        ModelOutputDefinition::getName)
                    .orElse(null);
            if (outputDefinition.isEmpty() || Strings.isNullOrEmpty(
                                                                    outputName)) {
                log.info("Empty output name found for extension {}. Extension will not consume any data.",
                         extension.name());
                return;
            }
            final var extensionOutputData = data.get(outputName);
            if (JsonUtils.empty(extensionOutputData)) {
                log.warn("No output from model for extension data named: {} for extension: {}",
                         outputName,
                         extension.name());
                return;
            }
            try {
                extension.consumeSync(context,
                                      messages,
                                      extensionOutputData,
                                      self);
            }
            catch (Exception e) {
                log.error("Error processing model output by extension {}: {}",
                          extension.name(),
                          AgentUtils.rootCause(e).getMessage());
            }
        });
    }

    private ArrayList<ModelOutputDefinition> populateOutputDefinitions(ProcessingMode processingMode) {
        final var outputDefinitions = new ArrayList<>(List.of(
                                                              new ModelOutputDefinition(OUTPUT_VARIABLE_NAME,
                                                                                        "Output generated by the agent",
                                                                                        outputSchema())));
        outputDefinitions.addAll(extensions.stream()
                .map(extension -> extension.outputSchema(processingMode))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList());
        return outputDefinitions;
    }

    private ModelOutput makeModelCall(AgentSetup mergedAgentSetup,
                                      ModelRunContext modelRunContext,
                                      List<ModelOutputDefinition> outputDefinitions,
                                      List<AgentMessage> messages,
                                      AgentRunContext<R> context) {
        ModelOutput modelOutput;
        final var stopwatch = Stopwatch.createStarted();
        try {
            modelOutput = mergedAgentSetup.getModel()
                    .compute(modelRunContext,
                             outputDefinitions,
                             messages,
                             knownTools,
                             new AgentToolRunner<>(self,
                                                   mergedAgentSetup,
                                                   toolRunApprovalSeeker,
                                                   context),
                             earlyTerminationStrategy,
                             agentMessagesPreProcessors)
                    .get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            modelOutput = ModelOutput.error(context.getOldMessages(),
                                            context.getModelUsageStats(),
                                            SentinelError.error(
                                                                ErrorType.GENERIC_MODEL_CALL_FAILURE,
                                                                "Model run interrupted."));
        }
        catch (Exception e) {
            modelOutput = ModelOutput.error(context.getOldMessages(),
                                            context.getModelUsageStats(),
                                            SentinelError.error(
                                                                ErrorType.GENERIC_MODEL_CALL_FAILURE,
                                                                AgentUtils
                                                                        .rootCause(e)
                                                                        .getMessage()));
        }
        EventUtils.raiseOutputEvent(modelRunContext, modelOutput, stopwatch);
        return modelOutput;
    }

    @SuppressWarnings("java:S107")
    private ModelOutput makeAsyncModelCall(AgentSetup mergedAgentSetup,
                                           ModelRunContext modelRunContext,
                                           List<ModelOutputDefinition> outputDefinitions,
                                           List<AgentMessage> messages,
                                           AgentRunContext<R> context,
                                           EarlyTerminationStrategy earlyTerminationStrategy,
                                           boolean isTextStreaming,
                                           Consumer<byte[]> streamHandler) {
        CompletableFuture<ModelOutput> modelFuture;
        ModelOutput modelOutput;
        final var stopwatch = Stopwatch.createStarted();

        final var toolRunner = new AgentToolRunner<>(self,
                                                     mergedAgentSetup,
                                                     toolRunApprovalSeeker,
                                                     context);
        try {
            if (isTextStreaming) {
                modelFuture = mergedAgentSetup.getModel()
                        .streamText(modelRunContext,
                                    messages,
                                    knownTools,
                                    toolRunner,
                                    earlyTerminationStrategy,
                                    streamHandler,
                                    agentMessagesPreProcessors);
            }
            else {
                modelFuture = mergedAgentSetup.getModel()
                        .stream(modelRunContext,
                                outputDefinitions,
                                messages,
                                knownTools,
                                toolRunner,
                                earlyTerminationStrategy,
                                streamHandler,
                                agentMessagesPreProcessors);
            }
            modelOutput = modelFuture.get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            modelOutput = ModelOutput.error(context.getOldMessages(),
                                            context.getModelUsageStats(),
                                            SentinelError.error(
                                                                ErrorType.NO_RESPONSE,
                                                                "Model run interrupted."));
        }
        catch (Exception e) {
            modelOutput = ModelOutput.error(context.getOldMessages(),
                                            context.getModelUsageStats(),
                                            SentinelError.error(
                                                                ErrorType.GENERIC_MODEL_CALL_FAILURE,
                                                                AgentUtils
                                                                        .rootCause(e)
                                                                        .getMessage()));
        }
        EventUtils.raiseOutputEvent(modelRunContext, modelOutput, stopwatch);
        return modelOutput;
    }

    private static void logEmptyData() {
        log.warn("No output data found in model output. Returning empty agent output.");
    }

    private static <T> Optional<AgentOutput<T>> handleErrorResponse(ModelOutput modelOutput) {
        if (modelOutput.getError() != null && !modelOutput.getError()
                .getErrorType()
                .equals(ErrorType.SUCCESS)) {
            log.error("Error returned in model run: {}",
                      modelOutput.getError().getMessage());
            return Optional.of(AgentOutput.error(modelOutput.getNewMessages(),
                                                 modelOutput.getAllMessages(),
                                                 modelOutput.getUsage(),
                                                 modelOutput.getError()));
        }
        return Optional.empty();
    }

    private String systemPrompt(AgentRunContext<R> context,
                                List<FactList> facts) throws JsonProcessingException {
        final var secondaryTasks = this.extensions.stream()
                .flatMap(extension -> extension.additionalSystemPrompts(context
                        .getRequest(),
                                                                        context,
                                                                        self,
                                                                        context.getProcessingMode())
                        .getTask()
                        .stream())
                .toList();
        final var knowledgeFromExtensions = this.extensions.stream()
                .flatMap(extension -> extension.facts(context.getRequest(),
                                                      context,
                                                      self).stream())
                .toList();
        final var knowledge = new ArrayList<>(knowledgeFromExtensions);
        knowledge.addAll(Objects.requireNonNullElseGet(facts, List::of));
        final var prompt = new SystemPrompt().setName(name())
                .setCoreInstructions("Your main job is to answer the user query as provided in user prompt in the `user_input` tag. "
                        + (!context
                                .getOldMessages()
                                .isEmpty()
                                        ? "Use the provided old messages for extra context and information. "
                                        : "") + ((!secondaryTasks.isEmpty())
                                                ? "Perform the provided secondary tasks as well and populate the output in "
                                                        + "designated output field for the task. "
                                                : "") + ((!knowledge.isEmpty())
                                                        ? "Use the provided knowledge and facts to enrich your responses."
                                                        : ""))
                .setPrimaryTask(SystemPrompt.Task.builder()
                        .objective(systemPrompt)
                        .tool(this.knownTools.values()
                                .stream()
                                .map(tool -> SystemPrompt.ToolSummary.builder()
                                        .name(tool.getToolDefinition().getId())
                                        .description(tool.getToolDefinition()
                                                .getDescription())
                                        .build())
                                .toList())
                        .build())
                .setSecondaryTask(secondaryTasks)
                .setFacts(knowledge);
        if (null != context.getRequestMetadata()) {
            prompt.setAdditionalData(new SystemPrompt.AdditionalData()
                    .setSessionId(context.getRequestMetadata().getSessionId())
                    .setUserId(context.getRequestMetadata().getUserId())
                    .setCustomParams(context.getRequestMetadata()
                            .getCustomParams()));
        }
        final var generatedSystemPrompt = xmlMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(prompt);
        log.debug("Final system prompt: {}", generatedSystemPrompt);
        return generatedSystemPrompt;

    }

    private List<AgentMessage> extensionMessages(R inputRequest,
                                                 AgentRunContext<R> context) {
        return extensions.stream().map(extension -> {
            final var messages = extension.messages(context,
                                                    self,
                                                    inputRequest);
            if (!messages.isEmpty()) {
                log.debug("Extension: {} added {} messages",
                          extension.name(),
                          messages.size());
            }
            return messages;
        }).flatMap(List::stream).toList();
    }


    @SneakyThrows
    private <U> String toXmlContent(U object) {
        final var xml = xmlMapper.writerWithDefaultPrettyPrinter()
                .withRootName("user_input")
                .writeValueAsString(toXmlNode(object));
        log.debug("User Prompt: {}", xml);
        return xml;
    }

    private <U> JsonNode toXmlNode(U object) {
        if (object.getClass().isAssignableFrom(String.class) || Primitives
                .isWrapperType(object.getClass())) {
            return xmlMapper.createObjectNode()
                    .put("data", Objects.toString(object));
        }
        return xmlMapper.valueToTree(object);
    }

    private static <R> void mergeModelOutput(final ModelOutputProcessingContext<R> processingContext,
                                             final ModelOutput modelOutput) {
        processingContext.messages.clear();
        processingContext.messages.addAll(modelOutput.getAllMessages());
    }
}
