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

package com.phonepe.sentinelai.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import org.apache.commons.lang3.ClassUtils;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.StreamConsumer;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategyResponse;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessContext;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessResult;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.IdentityOutputGenerator;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelAttributes;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ExternalTool;
import com.phonepe.sentinelai.core.tools.ToolDefinition;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.Pair;
import com.phonepe.sentinelai.models.errors.AgentMessagesPreProcessorExecutionFailedException;
import com.phonepe.sentinelai.models.errors.InvalidAgentMessagesException;
import com.phonepe.sentinelai.models.provider.HeaderAuth;
import com.phonepe.sentinelai.models.provider.Provider;
import com.phonepe.sentinelai.models.provider.RequestTransformer;
import com.phonepe.sentinelai.models.provider.RequestTransformerContext;
import com.phonepe.sentinelai.models.wire.SseEvent;
import com.phonepe.sentinelai.models.wire.SseReader;
import com.phonepe.sentinelai.models.wire.WireContext;
import com.phonepe.sentinelai.models.wire.WireLoggingMode;
import com.phonepe.sentinelai.models.wire.WirePayloadLogger;
import com.phonepe.sentinelai.models.wire.WireProtocol;
import com.phonepe.sentinelai.models.wire.WireResponse;
import com.phonepe.sentinelai.models.wire.WireStreamEvent;
import com.phonepe.sentinelai.models.wire.WireToolCall;
import com.phonepe.sentinelai.models.wire.WireUsage;

import dev.failsafe.FailsafeExecutor;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static com.phonepe.sentinelai.core.utils.EventUtils.raiseMessageReceivedEvent;
import static com.phonepe.sentinelai.core.utils.EventUtils.raiseMessageSentEvent;

/**
 * The model of this library: provider-neutral orchestration on top of one {@link WireProtocol}.
 * Owns the tool-run loop, the output generation modes, the pre-processor pipeline, stream
 * reassembly, usage merging and error mapping. All wire specifics live in the protocol; the
 * {@link Provider} owns the endpoint and the authentication. Calls travel through the caller's
 * {@link OkHttpClient}, so client interceptors (retries, metrics, circuit breaking) apply to every
 * model call.
 */
@Slf4j
@Getter
public class ConfiguredModel implements Model {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json");

    /**
     * Carries the HTTP status and body of a failed model call.
     */
    @Getter
    private static final class HttpModelCallException extends ModelHttpException {

        private final String body;

        HttpModelCallException(final int status, final String body, final Duration retryAfter) {
            super(status,
                  "Received HTTP error: [%d] %s".formatted(status, body),
                  retryAfter);
            this.body = body;
        }
    }

    /**
     * Carries the HTTP error details of a failed stream request.
     */
    @Getter
    static final class HttpStreamException extends ModelHttpException {

        private final String body;

        HttpStreamException(final int status, final String body, final Duration retryAfter) {
            super(status,
                  "Received HTTP error on model stream: [%d] %s".formatted(status, body),
                  retryAfter);
            this.body = body;
        }
    }

    /**
     * Accumulates the fragments of one tool call during a stream, merged per index.
     */
    private static final class ToolCallAccumulator {

        private final int index;
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        ToolCallAccumulator(final int index) {
            this.index = index;
        }

        static ToolCallAccumulator complete(final WireStreamEvent.ToolCallComplete complete) {
            final var accumulator = new ToolCallAccumulator(complete.getIndex());
            accumulator.id = complete.getId();
            accumulator.name = complete.getName();
            if (!Strings.isNullOrEmpty(complete.getArguments())) {
                accumulator.arguments.append(complete.getArguments());
            }
            return accumulator;
        }

        int indexOf() {
            return index;
        }

        ToolCallAccumulator merge(final WireStreamEvent.ToolCallDelta delta) {
            if (!Strings.isNullOrEmpty(delta.getId())) {
                id = delta.getId();
            }
            if (!Strings.isNullOrEmpty(delta.getName())) {
                name = Strings.isNullOrEmpty(name) ? delta.getName() : name + delta.getName();
            }
            if (!Strings.isNullOrEmpty(delta.getArgumentsFragment())) {
                arguments.append(delta.getArgumentsFragment());
            }
            return this;
        }

        WireToolCall toToolCall() {
            return new WireToolCall(id, name, arguments.toString());
        }
    }

    /**
     * One run's message state: translated wire messages plus the neutral history.
     */
    @Builder
    @Value
    protected static class AgentMessages {
        @NonNull
        List<JsonNode> wireMessages;
        @NonNull
        List<AgentMessage> allMessages;
        @NonNull
        List<AgentMessage> newMessages;
    }

    /**
     * Final output of the pre-processor pipeline.
     */
    @Builder
    @Value
    private static class PreProcessorExecutionResults {
        @NonNull
        List<AgentMessage> allMessages;
        List<AgentMessage> newMessages;
    }

    private final String modelName;
    private final String modelId;
    private final Provider provider;
    private final WireProtocol protocol;
    private final OkHttpClient httpClient;
    private final ModelOptions modelOptions;
    private final TokenCounter tokenCounter;
    private final List<RequestTransformer> requestTransformers;
    private final RequestRetryPolicy requestRetryPolicy;
    private final WireLoggingMode wireLogging;
    private final FailsafeExecutor<Object> retryExecutor;
    private final WirePayloadLogger wirePayloadLogger;

    /**
     * @param modelName           Display name of the model; also the wire id when {@code modelId} is
     *                            null.
     * @param modelId             Id sent in the request body; null falls back to {@code modelName}.
     * @param provider            Endpoint, endpoint prefix, authentication and wire protocols of
     *                            the model.
     * @param protocol            Wire protocol override; null uses the provider default. Must be
     *                            the provider default or one of its supported protocols, else the
     *                            constructor fails fast.
     * @param httpClient          OkHttp client used for every model call; never closed by the model.
     * @param modelOptions        Model options; null means {@link ModelOptions#DEFAULT}.
     * @param tokenCounter        Token counter; null means {@link GenericTokenCounter}.
     * @param requestTransformers Model-level request transformers applied after the provider-level
     *                            ones; may be null.
     * @param requestRetryPolicy  Retry policy of the model calls; null means
     *                            {@link RequestRetryPolicy#DEFAULT} (no retry).
     * @param wireLogging         Wire payload logging level; null means {@link WireLoggingMode#ON}.
     *                            A system property or an environment variable can override it;
     *                            see {@link WireLoggingMode#resolve(WireLoggingMode)}.
     */
    @Builder
    protected ConfiguredModel(final String modelName,
                              final String modelId,
                              @NonNull final Provider provider,
                              final WireProtocol protocol,
                              final OkHttpClient httpClient,
                              final ModelOptions modelOptions,
                              final TokenCounter tokenCounter,
                              final List<RequestTransformer> requestTransformers,
                              final RequestRetryPolicy requestRetryPolicy,
                              final WireLoggingMode wireLogging) {
        this.modelName = Objects.requireNonNullElse(modelName, "default-model");
        this.modelId = modelId;
        this.provider = provider;
        this.protocol = provider.protocolFor(protocol);
        this.httpClient = httpClient == null
                ? new OkHttpClient()
                : httpClient.newBuilder().build();
        this.modelOptions = Objects.requireNonNullElse(modelOptions, ModelOptions.DEFAULT);
        this.tokenCounter = Objects.requireNonNullElseGet(tokenCounter, GenericTokenCounter::new);
        this.requestTransformers = List.copyOf(Objects.requireNonNullElse(requestTransformers,
                                                                          List.of()));
        this.requestRetryPolicy = Objects.requireNonNullElse(requestRetryPolicy, RequestRetryPolicy.DEFAULT);
        this.wireLogging = wireLogging;
        this.retryExecutor = RequestRetryExecutors.executorFor(this.requestRetryPolicy);
        this.wirePayloadLogger = new WirePayloadLogger(WireLoggingMode.resolve(wireLogging));
    }

    /**
     * Convenience factory: Bearer token auth with the default endpoint prefix.
     *
     * @param baseUrl  Base URL of the provider.
     * @param apiKey   API key sent as the Bearer token.
     * @param protocol Wire protocol of the provider.
     * @return ConfiguredModel wired for Bearer auth.
     */
    public static ConfiguredModel bearer(final String modelName,
                                         final String baseUrl,
                                         final String apiKey,
                                         final WireProtocol protocol) {
        return ConfiguredModel.builder()
                .modelName(modelName)
                .provider(Provider.builder()
                        .baseUrl(baseUrl)
                        .protocol(protocol)
                        .auth(HeaderAuth.bearer(apiKey))
                        .build())
                .build();
    }

    @SuppressWarnings({
            "java:S107", "java:S3776"
    })
    @Override
    public CompletableFuture<ModelOutput> compute(final ModelRunContext context,
                                                  final Collection<ModelOutputDefinition> outputDefinitions,
                                                  final List<AgentMessage> oldMessages,
                                                  final Map<String, ExecutableTool> tools,
                                                  final ToolRunner toolRunner,
                                                  final EarlyTerminationStrategy earlyTerminationStrategy,
                                                  final List<AgentMessagesPreProcessor> messagesPreProcessors) {
        final var agentSetup = context.getAgentSetup();
        final var modelSettings = agentSetup.getModelSettings();
        final var mapper = agentSetup.getMapper();
        //This keeps getting
        // augmented with tool calls and reused across all iterations
        final var wireMessages = new ArrayList<>(translateAll(mapper,
                                                              AgentUtils.messagesAfterLastCompaction(oldMessages)));

        //There are for final model response
        final var allMessages = new ArrayList<>(oldMessages);
        final var newMessages = new ArrayList<AgentMessage>();

        //Stats for the run
        final var stats = context.getModelUsageStats();
        final var outputGenerationMode = determineMode(agentSetup, modelSettings);
        final var outputGenerator = Objects.requireNonNullElseGet(agentSetup
                .getOutputGenerationTool(), IdentityOutputGenerator::new);
        final var toolsForExecution = new HashMap<>(Objects
                .requireNonNullElseGet(tools, Map::of));
        final var generatedOutput = new AtomicReference<String>(null);
        final var lastResponseId = new AtomicReference<String>(null);
        final var schema = outputDefinitions.isEmpty()
                ? null
                : compliantSchema(mapper, outputDefinitions);
        if (outputGenerationMode.equals(OutputGenerationMode.TOOL_BASED)) {
            addOutputExtractionTool(toolsForExecution,
                                    schema,
                                    outputGenerator,
                                    generatedOutput);
        }
        if (log.isDebugEnabled()) {
            log.debug("Input messages: {}",
                      oldMessages.stream()
                              .map(AgentMessage::getMessageId)
                              .toList());
        }
        return CompletableFuture.supplyAsync(() -> {
            ModelOutput output = null;
            var prevMessages = findPreviousRunMessages(context.getRunId(), oldMessages);
            do {
                final var error = preProcessMessages(context,
                                                     mapper,
                                                     oldMessages,
                                                     messagesPreProcessors,
                                                     stats,
                                                     allMessages,
                                                     newMessages,
                                                     wireMessages)
                        .orElse(null);

                if (error != null) {
                    output = error;
                    break;
                }

                generatedOutput.set(null);
                final var ctx = buildWireContext(context,
                                                 mapper,
                                                 modelSettings,
                                                 toolsForExecution,
                                                 outputDefinitions,
                                                 outputGenerationMode,
                                                 schema,
                                                 context.getUserId(),
                                                 false);
                raiseMessageSentEvent(context, prevMessages, allMessages);
                final var stopwatch = Stopwatch.createStarted();
                stats.incrementRequestsForRun();
                final var requestBody = protocol.buildRequestBody(ctx, wireMessages);
                logDataDebug(mapper, "Request to model: {}", requestBody);

                final WireResponse response;
                try {
                    response = callModelWithChainBreakRetry(ctx,
                                                            requestBody,
                                                            buildTransformerContext(ctx,
                                                                                    context,
                                                                                    allMessages,
                                                                                    wireMessages),
                                                            wireMessages,
                                                            stats);
                }
                catch (final Exception e) {
                    return errorToModelOutput(context, e, newMessages, allMessages);
                }
                logDataDebug(mapper, "Response from model: {}", response);
                mergeUsage(stats, response.getUsage());
                lastResponseId.set(response.getResponseId());
                output = switch (response.getFinishReason()) {
                    case WireResponse.FinishReasons.STOP -> {
                        if (!Strings.isNullOrEmpty(response.getRefusal())) {
                            yield ModelOutput.error(oldMessages,
                                                    stats,
                                                    SentinelError.error(
                                                                        ErrorType.REFUSED,
                                                                        response.getRefusal()));
                        }
                        final var runToolsResponse = runTools(response.getToolCalls(),
                                                              context,
                                                              toolsForExecution,
                                                              toolRunner,
                                                              stats,
                                                              stopwatch,
                                                              generatedOutput,
                                                              lastResponseId,
                                                              wireMessages,
                                                              allMessages,
                                                              newMessages,
                                                              oldMessages);
                        yield runToolsResponse.orElseGet(() -> processOutput(context,
                                                                             response.getContent(),
                                                                             lastResponseId.get(),
                                                                             oldMessages,
                                                                             stats,
                                                                             allMessages,
                                                                             newMessages,
                                                                             stopwatch));
                    }
                    case WireResponse.FinishReasons.TOOL_CALLS -> runTools(response.getToolCalls(),
                                                                           context,
                                                                           toolsForExecution,
                                                                           toolRunner,
                                                                           stats,
                                                                           stopwatch,
                                                                           generatedOutput,
                                                                           lastResponseId,
                                                                           wireMessages,
                                                                           allMessages,
                                                                           newMessages,
                                                                           oldMessages)
                            .orElse(null);
                    case WireResponse.FinishReasons.LENGTH -> ModelOutput.error(oldMessages,
                                                                                stats,
                                                                                SentinelError
                                                                                        .error(ErrorType.LENGTH_EXCEEDED));
                    case WireResponse.FinishReasons.CONTENT_FILTER -> ModelOutput.error(oldMessages,
                                                                                        stats,
                                                                                        SentinelError
                                                                                                .error(ErrorType.FILTERED));
                    default -> ModelOutput.error(oldMessages,
                                                 stats,
                                                 SentinelError.error(ErrorType.UNKNOWN_FINISH_REASON,
                                                                     response.getFinishReason()));
                };

                if (shouldLoop(output)) {
                    final var modelOutput = Objects.requireNonNullElseGet(output,
                                                                          () -> new ModelOutput(null,
                                                                                                newMessages,
                                                                                                allMessages,
                                                                                                stats,
                                                                                                null));
                    final var agentMessages = AgentMessages
                            .builder()
                            .newMessages(newMessages)
                            .allMessages(allMessages)
                            .wireMessages(wireMessages)
                            .build();
                    output = evaluateRunTerminationStrategy(context,
                                                            earlyTerminationStrategy,
                                                            modelSettings,
                                                            modelOutput,
                                                            stats,
                                                            agentMessages);
                }
                prevMessages = List.copyOf(allMessages);
            } while (shouldLoop(output));
            return output;
        }, agentSetup.getExecutorService());
    }

    @Override
    public CompletableFuture<ModelOutput> stream(final ModelRunContext context,
                                                 final Collection<ModelOutputDefinition> outputDefinitions,
                                                 final List<AgentMessage> oldMessages,
                                                 final Map<String, ExecutableTool> tools,
                                                 final ToolRunner toolRunner,
                                                 final EarlyTerminationStrategy earlyTerminationStrategy,
                                                 final StreamConsumer streamHandler,
                                                 final List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
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
    public CompletableFuture<ModelOutput> streamText(final ModelRunContext context,
                                                     final List<AgentMessage> oldMessages,
                                                     final Map<String, ExecutableTool> tools,
                                                     final ToolRunner toolRunner,
                                                     final EarlyTerminationStrategy earlyTerminationStrategy,
                                                     final StreamConsumer streamHandler,
                                                     final List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
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

    @Override
    public int estimateTokenCount(final List<AgentMessage> messages,
                                  final AgentSetup agentSetup) {
        final var modelAttributes = AgentUtils.getIfNotNull(agentSetup.getModelSettings(),
                                                            ModelSettings::getModelAttributes,
                                                            ModelAttributes.DEFAULT_MODEL_ATTRIBUTES);
        return tokenCounter.estimateTokenCount(messages,
                                               this.modelOptions
                                                       .getTokenCountingConfig(),
                                               modelAttributes
                                                       .getEncodingType());
    }

    @SuppressWarnings({
            "java:S107", "java:S3776", "java:S135"
    })
    private CompletableFuture<ModelOutput> streamImpl(final ModelRunContext context,
                                                      final Collection<ModelOutputDefinition> outputDefinitions,
                                                      final List<AgentMessage> oldMessages,
                                                      final Map<String, ExecutableTool> tools,
                                                      final ToolRunner toolRunner,
                                                      final EarlyTerminationStrategy earlyTerminationStrategy,
                                                      final StreamConsumer streamHandler,
                                                      final Agent.StreamProcessingMode streamProcessingMode,
                                                      final List<AgentMessagesPreProcessor> messagesPreProcessors) {
        final var agentSetup = context.getAgentSetup();
        final var modelSettings = agentSetup.getModelSettings();
        final var mapper = agentSetup.getMapper();
        //This keeps getting
        // augmented with tool calls and reused across all iterations
        final var wireMessages = new ArrayList<>(translateAll(mapper,
                                                              AgentUtils.messagesAfterLastCompaction(oldMessages)));

        //There are for final model response
        final var allMessages = new ArrayList<>(oldMessages);
        final var newMessages = new ArrayList<AgentMessage>();

        //Stats for the run
        final var stats = context.getModelUsageStats();
        final var toolsForExecution = new HashMap<>(Objects
                .requireNonNullElseGet(tools, Map::of));
        final var outputGenerationMode = determineMode(agentSetup, modelSettings);
        final var outputGenerator = Objects.requireNonNullElseGet(agentSetup
                .getOutputGenerationTool(), IdentityOutputGenerator::new);
        final var generatedOutput = new AtomicReference<String>(null);
        final var lastResponseId = new AtomicReference<String>(null);
        final var schema = outputDefinitions.isEmpty()
                ? null
                : compliantSchema(mapper, outputDefinitions);
        if (streamProcessingMode.equals(
                                        Agent.StreamProcessingMode.TYPED) && outputGenerationMode
                                                .equals(OutputGenerationMode.TOOL_BASED)) {
            addOutputExtractionTool(toolsForExecution,
                                    schema,
                                    outputGenerator,
                                    generatedOutput);
        }
        return CompletableFuture.supplyAsync(() -> {
            ModelOutput output = null;
            var prevMessages = findPreviousRunMessages(context.getRunId(), oldMessages);
            do {
                final var error = preProcessMessages(context,
                                                     mapper,
                                                     oldMessages,
                                                     messagesPreProcessors,
                                                     stats,
                                                     allMessages,
                                                     newMessages,
                                                     wireMessages).orElse(null);
                if (error != null) {
                    output = error;
                    break;
                }
                final var ctx = buildWireContext(context,
                                                 mapper,
                                                 modelSettings,
                                                 toolsForExecution,
                                                 outputDefinitions,
                                                 outputGenerationMode,
                                                 schema,
                                                 context.getUserId(),
                                                 true);
                final var stopwatch = Stopwatch.createStarted();
                stats.incrementRequestsForRun();
                final var requestBody = protocol.buildRequestBody(ctx, wireMessages);
                logDataDebug(mapper, "Request to model: {}", requestBody);
                raiseMessageSentEvent(context, prevMessages, allMessages);
                Stream<WireStreamEvent> eventStream;
                try {
                    eventStream = openSseStreamWithChainBreakRetry(ctx,
                                                                   requestBody,
                                                                   buildTransformerContext(ctx,
                                                                                           context,
                                                                                           allMessages,
                                                                                           wireMessages),
                                                                   wireMessages,
                                                                   stats)
                            .filter(event -> !event.isDoneSentinel())
                            .mapMulti((SseEvent event, Consumer<WireStreamEvent> consumer) -> protocol
                                    .decodeStreamEvent(ctx, event)
                                    .forEach(consumer::accept));

                }
                catch (Exception e) {
                    return errorToModelOutput(context, e, newMessages, allMessages);
                }
                try (eventStream) {
                    //We use the following to merge the pieces of response we get from stream into final output
                    final var responseData = new StringBuilder();
                    //We use the following to cobble together the fragments of tool call objects we get from the stream
                    final var toolCallData = new HashMap<Integer, ToolCallAccumulator>();
                    //Providers repeat the finish reason on trailing chunks (usage etc.). Only the first one is handled.
                    final var finishHandled = new AtomicBoolean(false);

                    final var outputs = eventStream.map(streamEvent -> {
                        ModelOutput eventOutput = null;
                        if (streamEvent instanceof WireStreamEvent.ContentDelta contentDelta) {
                            responseData.append(contentDelta.getContent());
                            streamHandler.consumeReasoningAndContent(null, contentDelta.getContent());
                        }
                        else if (streamEvent instanceof WireStreamEvent.ReasoningDelta reasoningDelta) {
                            streamHandler.consumeReasoningAndContent(reasoningDelta.getContent(), null);
                        }
                        else if (streamEvent instanceof WireStreamEvent.ToolCallDelta toolCallDelta) {
                            // Caution: the following is not for people with weak constitution
                            // The api sends fully formed objects with partial data in the field
                            // So we try to assemble the pieces together to form a complete object
                            final var node = toolCallData.compute(toolCallDelta.getIndex(),
                                                                  (idx, existing) -> existing == null
                                                                          ? new ToolCallAccumulator(idx).merge(
                                                                                                               toolCallDelta)
                                                                          : existing.merge(toolCallDelta));
                            logDataDebug(mapper,
                                         "Function till now: {} -> {}",
                                         node,
                                         node.toToolCall());
                        }
                        else if (streamEvent instanceof WireStreamEvent.ToolCallComplete toolCallComplete) {
                            // The provider sent the finished tool call item; replace any fragment
                            // state for this index so the complete values win
                            final var node = toolCallData.put(toolCallComplete.getIndex(),
                                                              ToolCallAccumulator.complete(toolCallComplete));
                            logDataDebug(mapper,
                                         "Complete function call: {} -> {}",
                                         node,
                                         toolCallData.get(toolCallComplete.getIndex())
                                                 .toToolCall());
                        }
                        else if (streamEvent instanceof WireStreamEvent.StreamUsageEvent usageEvent) {
                            mergeUsage(stats, usageEvent.getUsage());
                        }
                        else if (streamEvent instanceof WireStreamEvent.StreamFinishEvent finishEvent) {
                            if (finishHandled.compareAndSet(false, true)) {
                                logDataDebug(mapper, "Finish event from model: {}", finishEvent.getFinishReason());
                                if (finishEvent.getResponseId() != null) {
                                    lastResponseId.set(finishEvent.getResponseId());
                                }
                                wirePayloadLogger.streamResponse(modelName,
                                                                 finishEvent,
                                                                 responseData.toString(),
                                                                 toolCallData.values()
                                                                         .stream()
                                                                         .sorted(Comparator.comparing(
                                                                                                      ToolCallAccumulator::indexOf))
                                                                         .map(ToolCallAccumulator::toToolCall)
                                                                         .toList(),
                                                                 mapper);
                                if (finishEvent.getUsage() != null) {
                                    mergeUsage(stats, finishEvent.getUsage());
                                }
                                eventOutput = handleStreamFinish(finishEvent,
                                                                 streamProcessingMode,
                                                                 context,
                                                                 oldMessages,
                                                                 stats,
                                                                 allMessages,
                                                                 newMessages,
                                                                 stopwatch,
                                                                 responseData,
                                                                 toolCallData,
                                                                 generatedOutput,
                                                                 toolsForExecution,
                                                                 toolRunner,
                                                                 wireMessages);
                            }
                            else if (finishEvent.getUsage() != null) {
                                // Providers repeat the finish reason on trailing chunks that carry usage.
                                // The first finish is already handled, so only the usage is merged here.
                                mergeUsage(stats, finishEvent.getUsage());
                            }
                        }
                        return eventOutput;
                    }).filter(Objects::nonNull).toList();
                    //NOTE::DO NOT MERGE THE STREAM WITH BELOW
                    //The flow is intentionally done this way
                    // This needs to be done in two steps to ensure all chunks are consumed. Otherwise, some stuff like
                    // usage etc. will get missed. Usage for example comes only after the full response is received.
                    output = outputs.isEmpty() ? null : outputs.get(outputs.size() - 1);
                    if (shouldLoop(output)) {
                        final var modelOutput = Objects.requireNonNullElseGet(output,
                                                                              () -> new ModelOutput(null,
                                                                                                    newMessages,
                                                                                                    allMessages,
                                                                                                    stats,
                                                                                                    null));
                        final var receivedMessages = AgentMessages
                                .builder()
                                .newMessages(newMessages)
                                .allMessages(allMessages)
                                .wireMessages(wireMessages)
                                .build();
                        output = evaluateRunTerminationStrategy(context,
                                                                earlyTerminationStrategy,
                                                                modelSettings,
                                                                modelOutput,
                                                                stats,
                                                                receivedMessages);
                    }
                    prevMessages = List.copyOf(allMessages); // Keep a copy. we need to find delta
                }
            } while (shouldLoop(output));
            return output;
        }, agentSetup.getExecutorService());
    }

    @SuppressWarnings("java:S107")
    private ModelOutput handleStreamFinish(final WireStreamEvent.StreamFinishEvent finishEvent,
                                           final Agent.StreamProcessingMode streamProcessingMode,
                                           final ModelRunContext context,
                                           final List<AgentMessage> oldMessages,
                                           final ModelUsageStats stats,
                                           final ArrayList<AgentMessage> allMessages,
                                           final List<AgentMessage> newMessages,
                                           final Stopwatch stopwatch,
                                           final StringBuilder responseData,
                                           final Map<Integer, ToolCallAccumulator> toolCallData,
                                           final AtomicReference<String> generatedOutput,
                                           final Map<String, ExecutableTool> toolsForExecution,
                                           final ToolRunner toolRunner,
                                           final List<JsonNode> wireMessages) {
        final var finishReason = finishEvent.getFinishReason();
        return switch (finishReason) {
            case WireResponse.FinishReasons.STOP -> {
                if (!Strings.isNullOrEmpty(finishEvent.getRefusal())) {
                    yield ModelOutput.error(oldMessages,
                                            stats,
                                            SentinelError.error(ErrorType.REFUSED, finishEvent.getRefusal()));
                }
                // Output handling is a little different for streaming and non-streaming cases
                // For streaming it looks like VLLM etc. are not supporting tool calls properly
                // So we do the old-fashioned way and use fragments collected during streaming
                // to cobble together the final output
                if (streamProcessingMode.equals(Agent.StreamProcessingMode.TYPED)) {

                    yield processOutput(context,
                                        responseData.toString(),
                                        //We just take what we gathered return that
                                        finishEvent.getResponseId(),
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
                                                 finishEvent.getResponseId(),
                                                 oldMessages,
                                                 stats,
                                                 allMessages,
                                                 newMessages,
                                                 stopwatch);
                }
            }
            case WireResponse.FinishReasons.TOOL_CALLS -> {

                //Model is waiting for us to run tools and respond back
                final var calls = toolCallData
                        .values()
                        .stream()
                        .sorted(Comparator.comparing(ToolCallAccumulator::indexOf))
                        .map(ToolCallAccumulator::toToolCall)
                        .toList();

                if (!calls.isEmpty()) {
                    final var agentMessages = AgentMessages
                            .builder()
                            .newMessages(newMessages)
                            .allMessages(allMessages)
                            .wireMessages(wireMessages)
                            .build();
                    handleToolCalls(context,
                                    toolsForExecution,
                                    toolRunner,
                                    calls,
                                    agentMessages,
                                    stats,
                                    stopwatch,
                                    finishEvent.getResponseId());
                    toolCallData.clear();
                    if (generatedOutput.get() != null) {
                        //If the output generator was called, we use the generated output
                        if (streamProcessingMode.equals(Agent.StreamProcessingMode.TYPED)) {

                            yield processOutput(context,
                                                generatedOutput.get(),
                                                finishEvent.getResponseId(),
                                                oldMessages,
                                                stats,
                                                allMessages,
                                                newMessages,
                                                stopwatch);
                        }
                        else {

                            yield processStreamingOutput(context,
                                                         generatedOutput.get(),
                                                         finishEvent.getResponseId(),
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
            case WireResponse.FinishReasons.LENGTH -> ModelOutput.error(oldMessages,
                                                                        stats,
                                                                        SentinelError.error(ErrorType.LENGTH_EXCEEDED));
            case WireResponse.FinishReasons.CONTENT_FILTER -> ModelOutput.error(oldMessages,
                                                                                stats,
                                                                                SentinelError.error(
                                                                                                    ErrorType.FILTERED));
            default -> ModelOutput.error(oldMessages,
                                         stats,
                                         SentinelError.error(ErrorType.UNKNOWN_FINISH_REASON, finishReason));
        };
    }

    /**
     * Executes one blocking model call.
     */
    private WireResponse callModel(final WireContext ctx,
                                   final ObjectNode requestBody,
                                   final RequestTransformerContext transformerContext) throws IOException {
        try (final var response = retryExecutor.get(() -> execute(ctx, requestBody, transformerContext))) {
            final var body = response.body();
            final var bytes = body == null ? new byte[0] : body.bytes();
            wirePayloadLogger.response(modelName, new String(bytes, StandardCharsets.UTF_8));
            final var json = ctx.getMapper().readTree(bytes);
            return protocol.decodeResponse(ctx, json);
        }
    }

    /**
     * Executes one blocking model call; on a chain break (the provider rejected the
     * {@code previous_response_id} of a chained request) retries once with the full history.
     */
    private WireResponse callModelWithChainBreakRetry(final WireContext ctx,
                                                      final ObjectNode requestBody,
                                                      final RequestTransformerContext transformerContext,
                                                      final List<JsonNode> wireMessages,
                                                      final ModelUsageStats stats) throws IOException {
        try {
            return callModel(ctx, requestBody, transformerContext);
        }
        catch (final Exception e) {
            final var retryBody = chainBreakRetryBody(ctx, e, wireMessages);
            if (retryBody.isEmpty()) {
                throw e;
            }
            stats.incrementRequestsForRun();
            return callModel(ctx, retryBody.get(), transformerContext);
        }
    }

    /**
     * Opens the model stream; on a chain break (the provider rejected the
     * {@code previous_response_id} of a chained request) retries once with the full history. A
     * stream that already delivered events is never retried: the failure surfaces before the
     * first frame or not at all.
     */
    private Stream<SseEvent> openSseStreamWithChainBreakRetry(final WireContext ctx,
                                                              final ObjectNode requestBody,
                                                              final RequestTransformerContext transformerContext,
                                                              final List<JsonNode> wireMessages,
                                                              final ModelUsageStats stats) {
        try {
            return openSseStream(ctx, requestBody, transformerContext);
        }
        catch (final Exception e) {
            final var retryBody = chainBreakRetryBody(ctx, e, wireMessages);
            if (retryBody.isEmpty()) {
                throw e;
            }
            stats.incrementRequestsForRun();
            return openSseStream(ctx, retryBody.get(), transformerContext);
        }
    }

    /**
     * Builds the chain-break retry body when a chained call was rejected because its
     * {@code previous_response_id} is unknown or expired; empty when the failure is not a
     * retryable chain break. The retry carries the full history with no anchor, and chaining
     * stays on so the retried response is stored and becomes the new chain anchor.
     */
    private Optional<ObjectNode> chainBreakRetryBody(final WireContext ctx,
                                                     final Throwable error,
                                                     final List<JsonNode> wireMessages) {
        if (ctx.getResponseChaining() == ModelOptions.ResponseChaining.OFF) {
            return Optional.empty();
        }
        final var rootCause = AgentUtils.rootCause(error);
        final int status;
        final String errorBody;
        if (rootCause instanceof HttpModelCallException httpError) {
            status = httpError.getStatus();
            errorBody = httpError.body;
        }
        else if (rootCause instanceof HttpStreamException streamError) {
            status = streamError.getStatus();
            errorBody = streamError.body;
        }
        else {
            return Optional.empty();
        }
        if (!protocol.isChainBreakError(status, parseErrorBody(ctx.getMapper(), errorBody))) {
            return Optional.empty();
        }
        log.warn("Chained model call rejected with status {} (previous_response_id unknown or "
                + "expired); retrying once with the full history", status);
        return protocol.buildChainBreakRetryBody(ctx, wireMessages);
    }

    /**
     * Executes one blocking HTTP call; fails with {@link HttpModelCallException} on a non-2xx response.
     */
    private Response execute(final WireContext ctx,
                             final ObjectNode requestBody,
                             final RequestTransformerContext transformerContext) throws IOException {
        final var executed = httpClient.newCall(buildOkRequest(ctx, requestBody, transformerContext)).execute();
        if (!executed.isSuccessful()) {
            throw httpError(executed, HttpModelCallException::new);
        }
        return executed;
    }

    /**
     * Builds the HTTP error of a non-2xx response; reads body and {@code Retry-After}.
     */
    private <E extends ModelHttpException> E httpError(final Response response,
                                                       final HttpErrorFactory<E> errorFactory)
            throws IOException {
        try (response) {
            final var retryAfter = ModelHttpException.parseRetryAfter(response.header("Retry-After"));
            final var body = response.body();
            final var bytes = body == null ? new byte[0] : body.bytes();
            final var text = new String(bytes, StandardCharsets.UTF_8);
            wirePayloadLogger.error(modelName, response.code(), text);
            return errorFactory.create(response.code(), text, retryAfter);
        }
    }

    /**
     * Factory of one HTTP error type from the status, body and Retry-After value.
     */
    @FunctionalInterface
    private interface HttpErrorFactory<E extends ModelHttpException> {

        E create(int status, String body, Duration retryAfter);
    }

    private Request buildOkRequest(final WireContext ctx,
                                   final ObjectNode requestBody,
                                   final RequestTransformerContext transformerContext) {
        final var builder = new Request.Builder()
                .url(protocol.endpoint(ctx))
                .header("Accept", ctx.isStreaming() ? "text/event-stream" : "application/json")
                .header("Content-Type", "application/json");
        if (provider.getAuth() != null) {
            provider.getAuth().apply(builder);
        }
        applyRequestTransformers(ctx, transformerContext, builder, requestBody);
        final var requestBytes = toBytes(ctx, requestBody);
        wirePayloadLogger.request(modelName, requestBytes);
        builder.post(RequestBody.create(requestBytes, JSON_MEDIA_TYPE));
        return builder.build();
    }

    /**
     * Builds the transformer context of one model call.
     */
    private static RequestTransformerContext buildTransformerContext(final WireContext ctx,
                                                                     final ModelRunContext runContext,
                                                                     final List<AgentMessage> messages,
                                                                     final List<JsonNode> wireMessages) {
        return RequestTransformerContext.builder()
                .wireContext(ctx)
                .sessionId(runContext.getSessionId())
                .agentName(runContext.getAgentName())
                .messages(List.copyOf(messages))
                .wireMessages(List.copyOf(wireMessages))
                .build();
    }

    /**
     * Applies the extension, provider and model level transformers in order; first failure
     * aborts with {@link RequestTransformFailedException}.
     */
    private void applyRequestTransformers(final WireContext ctx,
                                          final RequestTransformerContext transformerContext,
                                          final Request.Builder builder,
                                          final ObjectNode body) {
        // Extension level first: core contract on run id, body and headers.
        for (final var transformer : ctx.getExtensionRequestTransformers()) {
            final var headers = new HashMap<String, String>();
            try {
                transformer.transform(ctx.getRunId(), body, headers);
            }
            catch (final Exception e) {
                throw new RequestTransformFailedException(
                                                          "Request transformer failed: %s".formatted(transformer
                                                                  .getClass().getSimpleName()),
                                                          e);
            }
            headers.forEach(builder::header);
        }
        // Provider level, then model level: wire contract on the request builder.
        for (final var transformer : flattenedRequestTransformers()) {
            try {
                transformer.transform(builder, body, transformerContext);
            }
            catch (final Exception e) {
                throw new RequestTransformFailedException(
                                                          "Request transformer failed: %s".formatted(transformer
                                                                  .getClass().getSimpleName()),
                                                          e);
            }
        }
    }

    @SuppressWarnings("java:S107")
    private WireContext buildWireContext(final ModelRunContext runContext,
                                         final ObjectMapper mapper,
                                         final ModelSettings modelSettings,
                                         final Map<String, ExecutableTool> toolsForExecution,
                                         final Collection<ModelOutputDefinition> outputDefinitions,
                                         final OutputGenerationMode outputGenerationMode,
                                         final ObjectNode schema,
                                         final String userId,
                                         final boolean streaming) {
        return WireContext.builder()
                .modelName(modelName)
                .modelId(modelId)
                .baseUrl(provider.getBaseUrl())
                .endpointPrefix(provider.getEndpointPrefix())
                .userId(userId)
                .runId(runContext.getRunId())
                .modelSettings(modelSettings)
                .tools(toolsForExecution)
                .outputDefinitions(List.copyOf(outputDefinitions))
                .outputSchema(schema)
                .outputGenerationMode(outputGenerationMode)
                .extras(modelOptions.getExtras())
                .toolChoice(modelOptions.getToolChoice())
                .responseChaining(modelOptions.getResponseChaining())
                .streaming(streaming)
                .mapper(mapper)
                .extensionRequestTransformers(List.copyOf(runContext.getRequestTransformers()))
                .build();
    }

    /**
     * Flattens the provider and model level transformers in application order.
     */
    private List<RequestTransformer> flattenedRequestTransformers() {
        final var flattened = new ArrayList<RequestTransformer>(
                                                                provider.getRequestTransformers().size()
                                                                        + requestTransformers.size());
        flattened.addAll(provider.getRequestTransformers());
        flattened.addAll(requestTransformers);
        return flattened;
    }

    /**
     * Opens the SSE stream of one model call; retries only before the first event. A non-2xx
     * response becomes an {@link HttpStreamException}.
     */
    private Stream<SseEvent> openSseStream(final WireContext ctx,
                                           final ObjectNode requestBody,
                                           final RequestTransformerContext transformerContext) {
        return retryExecutor.get(() -> {
            final var future = new CompletableFuture<Stream<SseEvent>>();
            httpClient.newCall(buildOkRequest(ctx, requestBody, transformerContext)).enqueue(new Callback() {
                @Override
                public void onFailure(final Call call, final IOException e) {
                    future.completeExceptionally(e);
                }

                @Override
                public void onResponse(final Call call, final Response response) {
                    try {
                        if (!response.isSuccessful()) {
                            future.completeExceptionally(httpError(response, HttpStreamException::new));
                            return;
                        }
                        final var body = response.body();
                        if (body == null) {
                            response.close();
                            future.completeExceptionally(new IOException("No response body for model stream"));
                            return;
                        }
                        if (!future.complete(SseReader.stream(response, body)
                                .peek(event -> wirePayloadLogger.streamFrame(modelName, event)))) {
                            response.close();
                        }
                    }
                    catch (final Exception e) {
                        response.close();
                        future.completeExceptionally(e);
                    }
                }
            });
            return future.join();
        });
    }

    private byte[] toBytes(final WireContext ctx, final ObjectNode requestBody) {
        try {
            return ctx.getMapper().writeValueAsBytes(requestBody);
        }
        catch (final JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize request body", e);
        }
    }

    private List<JsonNode> translateAll(final ObjectMapper mapper, final List<AgentMessage> messages) {
        return messages.stream()
                .map(message -> translate(mapper, message))
                .toList();
    }

    private JsonNode translate(final ObjectMapper mapper, final AgentMessage message) {
        return protocol.messageCodec().translate(message, mapper);
    }

    private ModelOutput errorToModelOutput(final ModelRunContext context,
                                           final Throwable error,
                                           final List<AgentMessage> newMessages,
                                           final List<AgentMessage> allMessages) {
        // A transformer failure wraps its cause; the walk to the root cause would skip it and
        // lose the transformer-specific error type, so it is classified first.
        if (error instanceof RequestTransformFailedException transformFailed) {
            return createErrorResponse(context,
                                       newMessages,
                                       allMessages,
                                       ErrorType.REQUEST_TRANSFORM_FAILED,
                                       transformFailed.getMessage());
        }
        final var rootCause = AgentUtils.rootCause(error);
        log.error("Error calling model: %s -> %s".formatted(rootCause.getClass()
                .getSimpleName(), rootCause.getMessage()), error);
        // Looks like OkHttp sends out a variety of IOExceptions for network issues
        if (ClassUtils.isAssignable(rootCause.getClass(), IOException.class)) {
            return createErrorResponse(context,
                                       newMessages,
                                       allMessages,
                                       ErrorType.MODEL_CALL_COMMUNICATION_ERROR,
                                       rootCause.getMessage());
        }
        if (rootCause instanceof HttpModelCallException httpModelCallException) {
            final var errorType = protocol.classifyError(httpModelCallException.getStatus(),
                                                         parseErrorBody(context.getAgentSetup()
                                                                 .getMapper(),
                                                                        httpModelCallException.body));
            return createErrorResponse(context,
                                       newMessages,
                                       allMessages,
                                       errorType,
                                       httpModelCallException.getMessage());
        }
        return createErrorResponse(context,
                                   newMessages,
                                   allMessages,
                                   ErrorType.GENERIC_MODEL_CALL_FAILURE,
                                   rootCause.getMessage());
    }

    private JsonNode parseErrorBody(final ObjectMapper mapper, final String body) {
        if (Strings.isNullOrEmpty(body)) {
            return null;
        }
        try {
            return mapper.readTree(body);
        }
        catch (final JsonProcessingException e) {
            return null;
        }
    }

    private static ModelOutput createErrorResponse(final ModelRunContext context,
                                                   final List<AgentMessage> newMessages,
                                                   final List<AgentMessage> allMessages,
                                                   final ErrorType errorType,
                                                   final String message) {
        return ModelOutput.error(newMessages,
                                 allMessages,
                                 context.getModelUsageStats(),
                                 SentinelError.error(errorType, message));
    }

    private List<AgentMessage> findPreviousRunMessages(final String runId,
                                                       final List<AgentMessage> incomingMessages) {
        return List.copyOf(incomingMessages.stream()
                .filter(message -> !message.getRunId().equals(runId))
                .toList());
    }


    @SuppressWarnings("java:S107")
    private Optional<ModelOutput> runTools(final List<WireToolCall> receivedCalls,
                                           final ModelRunContext context,
                                           final Map<String, ExecutableTool> toolsForExecution,
                                           final ToolRunner toolRunner,
                                           final ModelUsageStats stats,
                                           final Stopwatch stopwatch,
                                           final AtomicReference<String> generatedOutput,
                                           final AtomicReference<String> lastResponseId,
                                           final ArrayList<JsonNode> wireMessages,
                                           final ArrayList<AgentMessage> allMessages,
                                           final ArrayList<AgentMessage> newMessages,
                                           final List<AgentMessage> oldMessages) {
        final var toolCalls = Objects.requireNonNullElseGet(receivedCalls,
                                                            List::<WireToolCall>of);

        if (!toolCalls.isEmpty()) {
            handleToolCalls(context,
                            toolsForExecution,
                            toolRunner,
                            toolCalls,
                            AgentMessages.builder()
                                    .newMessages(newMessages)
                                    .allMessages(allMessages)
                                    .wireMessages(wireMessages)
                                    .build(),
                            stats,
                            stopwatch,
                            lastResponseId.get());
            return Optional.ofNullable(generatedOutput.get())
                    .map(data -> processOutput(context,
                                               data,
                                               lastResponseId.get(),
                                               oldMessages,
                                               stats,
                                               allMessages,
                                               newMessages,
                                               stopwatch));

        }
        return Optional.empty();
    }

    private static void addOutputExtractionTool(final HashMap<String, ExecutableTool> toolsForExecution,
                                                final ObjectNode schema,
                                                final UnaryOperator<String> outputGenerator,
                                                final AtomicReference<String> generatedOutput) {
        final var toolDefinition = ToolDefinition.builder()
                .id(Agent.OUTPUT_GENERATOR_ID)
                .name(Agent.OUTPUT_GENERATOR_ID)
                .description("Generates the final output to be used by user.")
                .contextAware(true)
                .strictSchema(true)
                .terminal(true)
                .build();
        final var tool = new ExternalTool(toolDefinition,
                                          schema,
                                          (runContext, toolCallId, args) -> outputGenerationTool(
                                                                                                 outputGenerator,
                                                                                                 args,
                                                                                                 generatedOutput));
        toolsForExecution.put(Agent.OUTPUT_GENERATOR_ID, tool);
    }

    private static ExternalTool.ExternalToolResponse outputGenerationTool(final UnaryOperator<String> outputGenerator,
                                                                          final String args,
                                                                          final AtomicReference<String> generatedOutput) {

        try {
            final var output = outputGenerator.apply(args);
            if (!Strings.isNullOrEmpty(output)) {
                generatedOutput.set(output);
            }
            return new ExternalTool.ExternalToolResponse(output, ErrorType.SUCCESS);
        }
        catch (Throwable t) {
            final var rootCause = AgentUtils
                    .rootCause(t);
            log.error("Error generating output: " + rootCause
                    .getMessage(),
                      t);
            return new ExternalTool.ExternalToolResponse("Error running tool: "
                    + rootCause
                            .getMessage(),
                                                         ErrorType.TOOL_CALL_PERMANENT_FAILURE);
        }
    }

    private static boolean shouldLoop(final ModelOutput output) {
        return output == null
                || (output.getData() == null && output.getError() == null);
    }

    private static boolean isEarlyTermination(final EarlyTerminationStrategyResponse strategyResponse) {
        return Optional.ofNullable(strategyResponse)
                .map(response -> response
                        .getResponseType() == EarlyTerminationStrategyResponse.ResponseType.TERMINATE)
                .orElse(false);
    }

    private ModelOutput evaluateRunTerminationStrategy(final ModelRunContext context,
                                                       final EarlyTerminationStrategy earlyTerminationStrategy,
                                                       final ModelSettings modelSettings,
                                                       final ModelOutput output,
                                                       final ModelUsageStats stats,
                                                       final AgentMessages agentMessages) {
        final var strategyResponse = earlyTerminationStrategy.evaluate(
                                                                       modelSettings,
                                                                       context,
                                                                       output);
        if (isEarlyTermination(strategyResponse)) {
            return ModelOutput.error(Optional.ofNullable(output)
                    .map(ModelOutput::getAllMessages)
                    .orElse(List.of()),
                                     stats,
                                     new SentinelError(strategyResponse
                                             .getErrorType(),
                                                       strategyResponse
                                                               .getReason()));
        }
        if (null != strategyResponse && strategyResponse.getResponseType()
                == EarlyTerminationStrategyResponse.ResponseType.INSTRUCT) {
            sendFeedbackToModel(context, strategyResponse.getReason(), agentMessages);
        }
        return output;
    }

    /**
     * Adds the feedback instruction from an early termination strategy to the model
     * conversation as a user message. The model receives the instruction on the next
     * model call of the same run. The run continues.
     */
    private void sendFeedbackToModel(final ModelRunContext context,
                                     final String instruction,
                                     final AgentMessages agentMessages) {
        if (Strings.isNullOrEmpty(instruction)) {
            log.warn("Early termination strategy requested feedback with an empty instruction. Ignoring.");
            return;
        }
        log.warn("Sending feedback to model in run {}: {}", context.getRunId(), instruction);
        final var feedbackMessage = new GenericText(context.getSessionId(),
                                                    context.getRunId(),
                                                    AgentGenericMessage.Role.USER,
                                                    instruction);
        final var messagesBeforeFeedback = List.copyOf(agentMessages.getAllMessages());
        agentMessages.getAllMessages().add(feedbackMessage);
        agentMessages.getNewMessages().add(feedbackMessage);
        agentMessages.getWireMessages().add(translate(context.getAgentSetup().getMapper(), feedbackMessage));
        final var stopwatch = Stopwatch.createStarted();
        raiseMessageReceivedEvent(context,
                                  List.of(feedbackMessage),
                                  agentMessages.getAllMessages(),
                                  stopwatch);
        raiseMessageSentEvent(context,
                              messagesBeforeFeedback,
                              agentMessages.getAllMessages());
    }

    private ModelOutput processOutput(final ModelRunContext context,
                                      final String content,
                                      final String responseId,
                                      final List<AgentMessage> oldMessages,
                                      final ModelUsageStats stats,
                                      final ArrayList<AgentMessage> allMessages,
                                      final List<AgentMessage> newMessages,
                                      final Stopwatch stopwatch) {
        if (!Strings.isNullOrEmpty(content)) {
            final var newMessage = new StructuredOutput(context.getSessionId(),
                                                        context.getRunId(),
                                                        null,
                                                        null,
                                                        content,
                                                        stats,
                                                        stopwatch.elapsed(
                                                                          TimeUnit.MILLISECONDS),
                                                        responseId);
            allMessages.add(newMessage);
            newMessages.add(newMessage);
            raiseMessageReceivedEvent(context, List.of(newMessage), allMessages, stopwatch);
            try {
                return ModelOutput.success(context.getAgentSetup()
                        .getMapper()
                        .readTree(content), newMessages, allMessages, stats);
            }
            catch (JsonProcessingException e) {
                return ModelOutput.error(oldMessages,
                                         stats,
                                         SentinelError.error(
                                                             ErrorType.JSON_ERROR,
                                                             e));
            }
        }
        return ModelOutput.error(oldMessages,
                                 stats,
                                 SentinelError.error(ErrorType.NO_RESPONSE));

    }

    /**
     * In case of streaming output, the text content is directly passed as a response as a text node in the model
     * output.
     */
    private ModelOutput processStreamingOutput(final ModelRunContext context,
                                               final String content,
                                               final String responseId,
                                               final List<AgentMessage> oldMessages,
                                               final ModelUsageStats stats,
                                               final ArrayList<AgentMessage> allMessages,
                                               final List<AgentMessage> newMessages,
                                               final Stopwatch stopwatch) {
        //Model has sent all response
        if (!Strings.isNullOrEmpty(content)) {
            final var newMessage = new Text(context.getSessionId(),
                                            context.getRunId(),
                                            null,
                                            null,
                                            content,
                                            stats,
                                            stopwatch.elapsed(
                                                              TimeUnit.MILLISECONDS),
                                            responseId); //Always text output
            allMessages.add(newMessage);
            newMessages.add(newMessage);
            raiseMessageReceivedEvent(context, List.of(newMessage), allMessages, stopwatch);
            return ModelOutput.success(context.getAgentSetup()
                    .getMapper()
                    .createObjectNode()
                    .textNode(content), newMessages, allMessages, stats);
        }

        return ModelOutput.error(oldMessages,
                                 stats,
                                 SentinelError.error(ErrorType.NO_RESPONSE));
    }

    private static boolean toolsDisabled(final ModelSettings modelSettings) {
        return null != modelSettings && Objects.requireNonNullElse(modelSettings.getDisableTools(), false);
    }

    private static OutputGenerationMode determineMode(final AgentSetup agentSetup,
                                                      final ModelSettings modelSettings) {
        if (toolsDisabled(modelSettings)) {
            log.info("Tools are disabled for this model. Using structured output mode");
            return OutputGenerationMode.STRUCTURED_OUTPUT;
        }

        return Objects.requireNonNullElse(agentSetup
                .getOutputGenerationMode(), OutputGenerationMode.TOOL_BASED);
    }

    private void logDataDebug(final ObjectMapper mapper, final String fmtStr, final Object... nodes) {
        if (log.isDebugEnabled()) {
            try {
                log.debug(fmtStr,
                          mapper.writerWithDefaultPrettyPrinter()
                                  .writeValueAsString(nodes.length == 1 ? nodes[0] : List.of(nodes)));
            }
            catch (JsonProcessingException e) {
                //Do nothing
            }
        }
    }

    private static void mergeUsage(final ModelUsageStats stats, final WireUsage usage) {
        if (null != usage) {
            stats.incrementRequestTokens(WireUsage.orZero(usage.getInputTokens()))
                    .incrementResponseTokens(WireUsage.orZero(usage.getOutputTokens()))
                    .incrementTotalTokens(WireUsage.orZero(usage.getTotalTokens()))
                    .incrementRequestAudioTokens(WireUsage.orZero(usage.getInputAudioTokens()))
                    .incrementRequestCachedTokens(WireUsage.orZero(usage.getInputCachedTokens()))
                    .incrementResponseAudioTokens(WireUsage.orZero(usage.getOutputAudioTokens()))
                    .incrementResponseReasoningTokens(WireUsage.orZero(usage.getOutputReasoningTokens()));
        }
    }

    /**
     * Handle tool calls from the model
     */
    private void handleToolCalls(final ModelRunContext context,
                                 final Map<String, ExecutableTool> tools,
                                 final ToolRunner toolRunner,
                                 final List<WireToolCall> toolCalls,
                                 final AgentMessages agentMessages,
                                 final ModelUsageStats stats,
                                 final Stopwatch stopwatch,
                                 final String responseId) {
        final var prevMessages = List.copyOf(agentMessages.getAllMessages());
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
                        stopwatch,
                        responseId);
        raiseMessageSentEvent(context,
                              prevMessages,
                              agentMessages.getAllMessages());
    }

    @SuppressWarnings("java:S107")
    private void handleToolCalls(final String agentName,
                                 final String runId,
                                 final String sessionId,
                                 final String userId,
                                 final AgentSetup agentSetup,
                                 final Map<String, ExecutableTool> tools,
                                 final ToolRunner toolRunner,
                                 final List<WireToolCall> toolCalls,
                                 final AgentMessages agentMessages,
                                 final ModelUsageStats stats,
                                 final Stopwatch stopwatch,
                                 final String responseId) {
        final var seenToolCallIds = new HashSet<String>();
        final var mapper = agentSetup.getMapper();
        final var toolCallMessages = toolCalls.stream()
                .filter(toolCall -> !Strings.isNullOrEmpty(toolCall.getId()))
                .filter(toolCall -> seenToolCallIds.add(toolCall.getId()))
                .map(toolCall -> new ToolCall(sessionId,
                                              runId,
                                              null,
                                              null,
                                              toolCall.getId(),
                                              toolCall.getName(),
                                              toolCall.getArgumentsJson(),
                                              responseId))
                .toList();

        raiseMessageReceivedEvent(agentName,
                                  runId,
                                  sessionId,
                                  userId,
                                  agentSetup,
                                  ImmutableList.<AgentMessage>builder()
                                          .addAll(toolCallMessages)
                                          .build(),
                                  ImmutableList.<AgentMessage>builder()
                                          .addAll(agentMessages.getAllMessages())
                                          .addAll(toolCallMessages)
                                          .build(),
                                  stopwatch);
        final var jobs = toolCallMessages.stream()
                .map(toolCallMessage -> CompletableFuture.supplyAsync(() -> {
                    final var toolCallResponse = callTool(sessionId,
                                                          runId,
                                                          tools,
                                                          toolRunner,
                                                          toolCallMessage);
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
                    agentMessages.getWireMessages()
                            .add(translate(mapper, toolCallMessage));
                    agentMessages.getWireMessages()
                            .add(translate(mapper, toolCallResponse));
                    agentMessages.getAllMessages().add(toolCallMessage);
                    agentMessages.getNewMessages().add(toolCallMessage);
                    agentMessages.getAllMessages().add(toolCallResponse);
                    agentMessages.getNewMessages().add(toolCallResponse);
                    stats.incrementToolCallsForRun();
                });
    }

    private static ToolCallResponse callTool(final String sessionId,
                                             final String runId,
                                             final Map<String, ExecutableTool> tools,
                                             final ToolRunner toolRunner,
                                             final ToolCall toolCallMessage) {
        return null != toolRunner ? toolRunner.runTool(tools, toolCallMessage)
                : new ToolCallResponse(sessionId,
                                       runId,
                                       toolCallMessage.getToolCallId(),
                                       toolCallMessage.getToolName(),
                                       ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                       "Tool runner not provided for tool call %s[%s]"
                                               .formatted(toolCallMessage
                                                       .getToolCallId(),
                                                          toolCallMessage
                                                                  .getToolName()),
                                       LocalDateTime.now());
    }

    /**
     * Provides a compliant JSON schema for a map having the different outputs as entries.
     * Key of the map is {@link ModelOutputDefinition#getName()} and value schema is set to
     * {@link ModelOutputDefinition#getSchema()}
     *
     * @param outputDefinitions List of output definitions from the agent and it's extensions
     * @return Compliant schema
     */
    private static ObjectNode compliantSchema(final ObjectMapper mapper,
                                              final Collection<ModelOutputDefinition> outputDefinitions) {
        final var schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        final var fields = mapper.createArrayNode();
        schema.set("required", fields);
        final var propertiesNode = mapper.createObjectNode();
        schema.set("properties", propertiesNode);

        outputDefinitions.forEach(outputDefinition -> {
            fields.add(outputDefinition.getName());
            propertiesNode.set(outputDefinition.getName(),
                               outputDefinition.getSchema());
        });
        return schema;
    }

    /**
     * This will run pre-processors in sequence and replace messages content with pre-processed output
     *
     * @param context               Execution context
     * @param oldMessages           Incoming messages to model run call
     * @param messagesPreProcessors List of preprocessors
     * @param stats                 Usage stats might be updated
     * @param allMessages           All messages currently in context
     * @param newMessages           New messages generated in the context
     * @param wireMessages          Wire format messages converted from allMessages
     * @return Error if something has failed during pre-processor runs or empty if all good
     */
    @SuppressWarnings("java:S107")
    private Optional<ModelOutput> preProcessMessages(final ModelRunContext context,
                                                     final ObjectMapper mapper,
                                                     final List<AgentMessage> oldMessages,
                                                     final List<AgentMessagesPreProcessor> messagesPreProcessors,
                                                     final ModelUsageStats stats,
                                                     final List<AgentMessage> allMessages,
                                                     final List<AgentMessage> newMessages,
                                                     final List<JsonNode> wireMessages) {
        final var systemPrompt = allMessages.stream()
                .filter(msg -> msg.getMessageType() == AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE)
                .findFirst()
                .orElse(null);
        if (null == systemPrompt) {
            log.error("System prompt message is missing in the messages.");
        }
        try {
            final var preProcessorsOutput = runPreProcessors(messagesPreProcessors,
                                                             context,
                                                             allMessages,
                                                             newMessages);
            preProcessorsOutput.ifPresent(processedAgentMessages -> {
                // If pre-processing has returned responses
                // Replace contents to be sent to the model
                allMessages.clear();
                newMessages.clear();
                wireMessages.clear();

                allMessages.addAll(processedAgentMessages.allMessages);
                newMessages.addAll(processedAgentMessages.newMessages);
                //Add system prompt back if it was missing in the pre-processor output
                if (null != systemPrompt && allMessages.stream()
                        .noneMatch(msg -> msg.getMessageType() == AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE)) {
                    allMessages.add(0, systemPrompt);
                }
                if (null != systemPrompt && newMessages.stream()
                        .noneMatch(msg -> msg.getMessageType() == AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE)) {
                    newMessages.add(0, systemPrompt);
                }
                wireMessages.addAll(translateAll(mapper, AgentUtils.messagesAfterLastCompaction(allMessages)));
            });
        }
        catch (InvalidAgentMessagesException ie) {
            log.error("Preprocessor returned invalid messages ", ie);
            return Optional.of(ModelOutput.error(oldMessages,
                                                 stats,
                                                 SentinelError.error(
                                                                     ErrorType.PREPROCESSOR_MESSAGES_OUTPUT_INVALID,
                                                                     ie.getMessage())));
        }
        catch (Exception e) {
            final var message = AgentUtils.rootCause(e).getMessage();
            log.error("Error running preprocessor: " + message, e);
            return Optional.of(ModelOutput.error(oldMessages,
                                                 stats,
                                                 SentinelError.error(
                                                                     ErrorType.PREPROCESSOR_RUN_FAILURE,
                                                                     message)));
        }
        return Optional.empty();
    }

    /**
     *
     * Executes the given set of pre-processors.
     * The processors are executed as a chain/pipeline where a valid output(non-empty list of msgs) of one processor
     * is passed as an input for the next processor in the chain.
     *
     */
    private Optional<PreProcessorExecutionResults> runPreProcessors(final List<AgentMessagesPreProcessor> messagesPreProcessors,
                                                                    final ModelRunContext context,
                                                                    final List<AgentMessage> allMessages,
                                                                    final List<AgentMessage> newMessages) {
        if (messagesPreProcessors == null || messagesPreProcessors.isEmpty()) {
            log.trace("No agent messages pre-processors to be executed.");
            return Optional.empty();
        }

        var transformedAllMessages = List.copyOf(allMessages);
        var transformedNewMessages = List.copyOf(newMessages);

        final var ctx = AgentMessagesPreProcessContext.builder()
                .modelRunContext(context)
                .build();
        for (var processor : messagesPreProcessors) {
            AgentMessagesPreProcessResult response;
            try {
                response = processor.process(ctx,
                                             transformedAllMessages,
                                             transformedNewMessages);
            }
            catch (Exception e) {
                log.error("Error executing preprocessor: {}",
                          processor.getClass().getSimpleName(),
                          e);
                throw new AgentMessagesPreProcessorExecutionFailedException("Preprocessor %s failed: %s"
                        .formatted(processor.getClass().getSimpleName(),
                                   e.getMessage()), e);
            }

            final var candidateMessages = response.getTransformedMessages();
            if (candidateMessages != null) {
                validateTransformedAgentMessages(processor,
                                                 candidateMessages);
                transformedAllMessages = List.copyOf(candidateMessages);
            }

            if (response.getNewMessages() != null) {
                transformedNewMessages = List.copyOf(response.getNewMessages());
            }
        }

        // If nothing changed across the entire chain, indicate no-op to avoid unnecessary merging
        if (transformedAllMessages.equals(allMessages) && transformedNewMessages.equals(newMessages)) {
            return Optional.empty();
        }

        return Optional.of(PreProcessorExecutionResults.builder()
                .allMessages(transformedAllMessages)
                .newMessages(transformedNewMessages)
                .build());
    }

    private void validateTransformedAgentMessages(final AgentMessagesPreProcessor processor,
                                                  final List<AgentMessage> messages) {

        if (messages.isEmpty()) {
            throw InvalidAgentMessagesException.withMessage(
                                                            "Agent Messages returned by the processor: %s are invalid. Must be a non-empty list."
                                                                    .formatted(processor
                                                                            .getClass()
                                                                            .getSimpleName()));
        }

        if (!hasExactlyOneSystemPromptMessage(messages)) {
            throw InvalidAgentMessagesException.withMessage(
                                                            "Agent Messages returned by the processor: %s are invalid. Must contain one system prompt message."
                                                                    .formatted(processor
                                                                            .getClass()
                                                                            .getSimpleName()));
        }

        if (!hasAtLeastOneUserPromptMessage(messages)) {
            throw InvalidAgentMessagesException.withMessage(
                                                            "Agent Messages returned by the processor: %s are invalid. Must contain at least one user message."
                                                                    .formatted(processor
                                                                            .getClass()
                                                                            .getSimpleName()));
        }
    }

    private static boolean hasAtLeastOneUserPromptMessage(final List<AgentMessage> messages) {
        return messages.stream()
                .anyMatch(x -> x.getMessageType()
                        .equals(AgentMessageType.USER_PROMPT_REQUEST_MESSAGE));
    }

    private static boolean hasExactlyOneSystemPromptMessage(final List<AgentMessage> messages) {
        return messages.stream()
                .filter(x -> x.getMessageType()
                        .equals(AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE))
                .count() == 1;
    }

}
