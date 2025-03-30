package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.primitives.Primitives;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.CallableTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.ToolReader;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

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

    @FunctionalInterface
    public interface ToolRunner<S> {
        ToolCallResponse runTool(AgentRunContext<S> context, Map<String, CallableTool> tool, ToolCall toolCall);
    }

    private final Class<T> outputType;
    private final String systemPrompt;
    private final AgentSetup setup;
    private final List<AgentExtension> extensions;
    private final Map<String, CallableTool> knownTools = new ConcurrentHashMap<>();
    private final XmlMapper xmlMapper = new XmlMapper();

    @SneakyThrows
    protected Agent(
            @NonNull Class<T> outputType,
            @NonNull String systemPrompt,
            @NonNull AgentSetup setup,
            List<AgentExtension> extensions,
            Map<String, CallableTool> knownTools) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(systemPrompt), "Please provide a valid system prompt");

        this.outputType = outputType;
        this.systemPrompt = systemPrompt;
        this.setup = setup;
        this.extensions = Objects.requireNonNullElseGet(extensions, List::of);
        xmlMapper.registerModule(new JavaTimeModule());
        xmlMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_1_1, true);
        registerTools(knownTools);
    }

    public abstract String name();

    /**
     * Register toolboxes with the agent
     *
     * @param toolbox List of toolboxes
     * @return this
     */
    @SuppressWarnings("unchecked")
    public A registerToolboxes(final List<ToolBox> toolbox) {
        Objects.requireNonNullElseGet(toolbox, List::<ToolBox>of)
                .forEach(this::registerToolbox);
        return (A) this;
    }

    /**
     * Register a toolbox with the agent
     *
     * @param toolBox Toolbox to register
     * @return this
     */
    @SuppressWarnings("unchecked")
    public A registerToolbox(ToolBox toolBox) {
        registerTools(toolBox.tools());
        return (A) this;
    }

    /**
     * Register tools with the agent directly
     *
     * @param callableTools List of callable tools
     * @return this
     */
    public A registerTools(List<CallableTool> callableTools) {
        return registerTools(Objects.requireNonNullElseGet(callableTools, List::<CallableTool>of)
                                     .stream()
                                     .collect(toMap(tool -> tool.getToolDefinition().getName(),
                                                    Function.identity())));
    }

    /**
     * Register tools with the agent directly
     *
     * @param callableTools Map of callable tools
     * @return this
     */
    @SuppressWarnings("unchecked")
    public A registerTools(Map<String, CallableTool> callableTools) {
        final var tools = new HashMap<>(ToolReader.readTools(this));
        tools.putAll(Objects.requireNonNullElseGet(callableTools, Map::of));
        if (!tools.isEmpty()) {
            log.info("Discovered tools: {}", tools.keySet());
        }
        else {
            log.debug("No tools registered");
        }
        this.knownTools.putAll(tools);
        return (A) this;
    }

    /**
     * Execute the agent synchronously.
     *
     * @param request         Request object
     * @param requestMetadata Metadata for the request
     * @return Agent output. Please see {@link AgentOutput} for details.
     */
    public final AgentOutput<T> execute(
            R request,
            AgentRequestMetadata requestMetadata) {
        return execute(request, requestMetadata, null);
    }

    /**
     * Execute the agent synchronously.
     *
     * @param request         Request object
     * @param requestMetadata Metadata for the request
     * @param oldMessages     Old messages. List of old messages to be sent to the LLM for this run. If set to null,
     *                        messages are generated and consumed by the agent in this session.
     * @return Agent output. Please see {@link AgentOutput} for details.
     */
    public final AgentOutput<T> execute(
            R request,
            AgentRequestMetadata requestMetadata,
            List<AgentMessage> oldMessages) {
        return execute(request, requestMetadata, oldMessages, null);
    }

    /**
     * Execute the agent synchronously.
     *
     * @param request         Request object
     * @param requestMetadata Metadata for the request
     * @param oldMessages     Old messages. List of old messages to be sent to the LLM for this run. If set to null,
     *                        messages are generated and consumed by the agent in this session.
     * @param agentSetup      Setup for the agent. This is an override at runtime. If set to null, the setup provided
     *                        will be used. Whatever fields are provided are merged with the setup provided during
     * @return Agent output. Please see {@link AgentOutput} for details.
     */
    public final AgentOutput<T> execute(
            R request,
            AgentRequestMetadata requestMetadata,
            List<AgentMessage> oldMessages,
            AgentSetup agentSetup) {
        return executeAsync(request, requestMetadata, oldMessages, agentSetup).join();
    }

    /**
     * Execute the agent asynchronously. A full reply is returned as a future.
     *
     * @param request         Request object
     * @param requestMetadata Metadata for the request
     * @return Agent output. Please see {@link AgentOutput} for details.
     */
    public final CompletableFuture<AgentOutput<T>> executeAsync(
            R request,
            AgentRequestMetadata requestMetadata) {
        return executeAsync(request, requestMetadata, null);
    }

    /**
     * Execute the agent asynchronously. A full reply is returned as a future.
     *
     * @param request         Request object
     * @param requestMetadata Metadata for the request
     * @param oldMessages     Old messages. List of old messages to be sent to the LLM for this run. If set to null,
     *                        messages are generated and consumed by the agent in this session.
     * @return Agent output. Please see {@link AgentOutput} for details.
     */
    public final CompletableFuture<AgentOutput<T>> executeAsync(
            R request,
            AgentRequestMetadata requestMetadata,
            List<AgentMessage> oldMessages) {
        return executeAsync(request, requestMetadata, oldMessages, null);
    }

    /**
     * Execute the agent asynchronously. A full reply is returned as a future.
     *
     * @param request         Request object
     * @param requestMetadata Metadata for the request
     * @param oldMessages     Old messages. List of old messages to be sent to the LLM for this run. If set to null,
     *                        messages are generated and consumed by the agent in this session.
     * @param agentSetup      Setup for the agent. This is an override at runtime. If set to null, the setup provided
     *                        will be used. Whatever fields are provided are merged with the setup provided during
     *                        agent creation. If same fields are provided in both places, the ones provided at
     *                        runtime will get precedence.
     * @return Agent output. Please see {@link AgentOutput} for details.
     */
    @SuppressWarnings("unchecked")
    public final CompletableFuture<AgentOutput<T>> executeAsync(
            R request,
            AgentRequestMetadata requestMetadata,
            List<AgentMessage> oldMessages,
            AgentSetup agentSetup) {
        final var mergedAgentSetup = merge(agentSetup, this.setup);
        final var messages = new ArrayList<>(Objects.requireNonNullElse(oldMessages, List.of()));
        final var runId = UUID.randomUUID().toString();
        final var context = new AgentRunContext<>(runId,
                                                  request,
                                                  requestMetadata,
                                                  mergedAgentSetup,
                                                  null,
                                                  messages,
                                                  new ModelUsageStats());
        var finalSystemPrompt = "";
        try {
            finalSystemPrompt = systemPrompt(request, requestMetadata, messages);
        }
        catch (JsonProcessingException e) {
            log.error("Error serializing system prompt", e);
            return CompletableFuture.completedFuture(AgentOutput.error(messages,
                                                                       context.getModelUsageStats(),
                                                                       SentinelError.error(ErrorType.SERIALIZATION_ERROR,
                                                                                           e)));
        }
        log.debug("Final system prompt: {}", finalSystemPrompt);
        messages.add(new SystemPrompt(finalSystemPrompt, false, null));
        messages.add(new UserPrompt(toXmlContent(request), LocalDateTime.now()));
        return mergedAgentSetup.getModel()
                .exchange_messages(
                        context,
                        outputType,
                        knownTools,
                        this::runToolObserved,
                        this.extensions,
                        (A) this)
                .thenApplyAsync(response -> {
                    if (null != response.getUsage() && requestMetadata != null && requestMetadata.getUsageStats() != null) {
                        requestMetadata.getUsageStats().merge(response.getUsage());
                    }
                    return response;
                });
    }

    /**
     * Streaming execution. This should be used for text streaming applications like chat etc.
     *
     * @param request         Request object
     * @param requestMetadata Metadata for the request
     * @param streamHandler   Stream handler for the response
     * @return The response to be consumed by the client
     */
    public final CompletableFuture<AgentOutput<byte[]>> executeAsyncStreaming(
            R request,
            AgentRequestMetadata requestMetadata,
            Consumer<byte[]> streamHandler) {
        return executeAsyncStreaming(request, requestMetadata, streamHandler, null);
    }

    /**
     * Streaming execution. This should be used for text streaming applications like chat etc.
     *
     * @param request         Request object
     * @param requestMetadata Metadata for the request
     * @param streamHandler   Stream handler for the response
     * @param oldMessages     Old messages. List of old messages to be sent to the LLM for this run. If set to null,
     *                        messages are generated and consumed by the agent in this session.
     * @return The response to be consumed by the client
     */
    public final CompletableFuture<AgentOutput<byte[]>> executeAsyncStreaming(
            R request,
            AgentRequestMetadata requestMetadata,
            Consumer<byte[]> streamHandler, List<AgentMessage> oldMessages) {
        return executeAsyncStreaming(request, requestMetadata, streamHandler, oldMessages, null);
    }


    /**
     * Streaming execution. This should be used for text streaming applications like chat etc.
     *
     * @param request         Request object
     * @param requestMetadata Metadata for the request
     * @param streamHandler   Stream handler for the response
     * @param oldMessages     Old messages. List of old messages to be sent to the LLM for this run. If set to null,
     *                        messages are generated and consumed by the agent in this session.
     * @param agentSetup      Setup for the agent. This is an override at runtime. If set to null, the setup provided
     * @return The response to be consumed by the client
     */
    @SuppressWarnings("unchecked")
    public final CompletableFuture<AgentOutput<byte[]>> executeAsyncStreaming(
            R request,
            AgentRequestMetadata requestMetadata,
            Consumer<byte[]> streamHandler, List<AgentMessage> oldMessages,
            AgentSetup agentSetup) {
        final var mergedAgentSetup = merge(agentSetup, this.setup);
        final var messages = new ArrayList<>(Objects.requireNonNullElse(oldMessages, List.<AgentMessage>of())
                                                     .stream()
                                                     .filter(message -> !message.getMessageType()
                                                             .equals(AgentMessageType.SYSTEM_PROMPT_REQUEST))
                                                     .toList());
        final var runId = UUID.randomUUID().toString();
        final var context = new AgentRunContext<>(runId,
                                                  request,
                                                  requestMetadata,
                                                  mergedAgentSetup,
                                                  null,
                                                  messages,
                                                  new ModelUsageStats());
        var finalSystemPrompt = "";
        try {
            finalSystemPrompt = xmlMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(systemPrompt(request, requestMetadata, messages));
        }
        catch (JsonProcessingException e) {
            log.error("Error serializing system prompt", e);
            return CompletableFuture.completedFuture(AgentOutput.error(messages,
                                                                       context.getModelUsageStats(),
                                                                       SentinelError.error(ErrorType.SERIALIZATION_ERROR,
                                                                                           e)));
        }
        log.debug("Final system prompt: {}", finalSystemPrompt);
        messages.add(new SystemPrompt(finalSystemPrompt, false, null));
        messages.add(new UserPrompt(toXmlContent(request), LocalDateTime.now()));
        return mergedAgentSetup.getModel()
                .exchange_messages_streaming(
                        context,
                        knownTools,
                        this::runToolObserved,
                        this.extensions,
                        (A) this,
                        streamHandler)
                .thenApply(response -> {
                    if (null != response.getUsage() && requestMetadata != null && requestMetadata.getUsageStats() != null) {
                        requestMetadata.getUsageStats().merge(response.getUsage());
                    }
                    return response;
                });
    }

    private String systemPrompt(
            R request,
            final AgentRequestMetadata requestMetadata,
            List<AgentMessage> messages) throws JsonProcessingException {
        final var prompt = new SystemPromptSchema()
                .setCoreInstructions(
                        "Your main job is to answer the user query as provided in user prompt in the `user_input` tag. "
                                + (!messages.isEmpty()
                                   ? "Use the provided old messages for extra context and information." : ""))
                .setPrimaryTask(new SystemPromptSchema.PrimaryTask()
                                        .setRole(systemPrompt)
                                        .setTools(this.knownTools.values()
                                                          .stream()
                                                          .map(tool -> new SystemPromptSchema.ToolSummary()
                                                                  .setName(tool.getToolDefinition().getName())
                                                                  .setDescription(tool.getToolDefinition()
                                                                                          .getDescription()))
                                                          .toList()))
                .setSecondaryTasks(this.extensions
                                           .stream()
                                           .map(extension -> new SystemPromptSchema.SecondaryTask()
                                                   .setInstructions(extension.additionalSystemPrompts(request,
                                                                                                      requestMetadata,
                                                                                                      (A) this)))
                                           .toList());
        if (null != requestMetadata) {
            prompt.setAdditionalData(new SystemPromptSchema.AdditionalData()
                                             .setSessionId(requestMetadata.getSessionId())
                                             .setUserId(requestMetadata.getUserId()));
        }
        return xmlMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(prompt);

    }

    private static AgentSetup merge(final AgentSetup lhs, final AgentSetup rhs) {
        return AgentSetup.builder()
                .model(Objects.requireNonNull(value(lhs, rhs, AgentSetup::getModel), "Model is required"))
                .modelSettings(value(lhs, rhs, AgentSetup::getModelSettings))
                .mapper(Objects.requireNonNullElseGet(value(lhs, rhs, AgentSetup::getMapper), JsonUtils::createMapper))
                .executorService(Objects.requireNonNullElseGet(value(lhs, rhs, AgentSetup::getExecutorService),
                                                               Executors::newCachedThreadPool))
                .eventBus(Objects.requireNonNullElseGet(value(lhs, rhs, AgentSetup::getEventBus), EventBus::new))
                .build();
    }

    private static <T, R> R value(final T lhs, final T rhs, Function<T, R> mapper) {
        final var obj = lhs == null ? rhs : lhs;
        if (null != obj) {
            return mapper.apply(obj);
        }
        return null;
    }

    private ToolCallResponse runToolObserved(
            AgentRunContext<R> context,
            Map<String, CallableTool> tools,
            ToolCall toolCall) {
        context.getAgentSetup()
                .getEventBus()
                .notify(new ToolCalledAgentEvent(name(),
                                                 context.getRunId(),
                                                 AgentUtils.sessionId(context),
                                                 AgentUtils.userId(context),
                                                 toolCall.getToolCallId(),
                                                 toolCall.getToolName()));
        final var stopwatch = Stopwatch.createStarted();
        final var response = runTool(context, tools, toolCall);
        context.getAgentSetup()
                .getEventBus()
                .notify(new ToolCallCompletedAgentEvent(name(),
                                                        context.getRunId(),
                                                        AgentUtils.sessionId(context),
                                                        AgentUtils.userId(context),
                                                        toolCall.getToolCallId(),
                                                        toolCall.getToolName(),
                                                        response.isSuccess(),
                                                        response.getResponse(),
                                                        Duration.ofMillis(stopwatch.elapsed(TimeUnit.MILLISECONDS))));
        return response;
    }

    @SuppressWarnings("java:S3011")
    private ToolCallResponse runTool(
            AgentRunContext<R> context,
            Map<String, CallableTool> tools,
            ToolCall toolCall) {
        //TODO::RETRY LOGIC
        final var tool = tools.get(toolCall.getToolName());
        if (null != tool) {
            try {
                final var args = new ArrayList<>();
                if (tool.getToolDefinition().isContextAware()) {
                    args.add(context);
                }
                args.addAll(params(tool, toolCall.getArguments()));
                final var callable = tool.getCallable();
                callable.setAccessible(true);
                log.info("Calling tool: {} Tool call ID: {}", toolCall.getToolName(), toolCall.getToolCallId());
                var resultObject = callable.invoke(tool.getInstance(), args.toArray());
                return new ToolCallResponse(toolCall.getToolCallId(),
                                            toolCall.getToolName(),
                                            true,
                                            toStringContent(tool, resultObject),
                                            LocalDateTime.now());
            }
            catch (InvocationTargetException e) {
                log.info("Local error making tool call " + toolCall.getToolCallId(), e);
                final var rootCause = AgentUtils.rootCause(e);
                return new ToolCallResponse(toolCall.getToolCallId(),
                                            toolCall.getToolName(),
                                            false,
                                            "Tool call local failure: %s".formatted(rootCause.getMessage()),
                                            LocalDateTime.now());
            }
            catch (Exception e) {
                log.info("Error making tool call " + toolCall.getToolCallId(), e);
                final var rootCause = AgentUtils.rootCause(e);
                return new ToolCallResponse(toolCall.getToolCallId(),
                                            toolCall.getToolName(),
                                            false,
                                            "Tool call failed. Threw exception: %s".formatted(rootCause.getMessage()),
                                            LocalDateTime.now());
            }
        }
        return new ToolCallResponse(toolCall.getToolCallId(),
                                    toolCall.getToolName(),
                                    false,
                                    "Tool call failed. Invalid tool: %s".formatted(toolCall.getToolName()),
                                    LocalDateTime.now());

    }

    /**
     * Convert parameters string received from LLM to actual parameters for tool call
     *
     * @param tool   Actual tool to be called
     * @param params Parameters string to be converted
     * @return List of parameters to be passed to the tool/function
     */
    @SneakyThrows
    private List<Object> params(CallableTool tool, String params) {
        final var paramNodes = setup.getMapper().readTree(params);
        return tool.getToolDefinition()
                .getParameters()
                .entrySet()
                .stream()
                .map(entry -> {
                    final var paramName = entry.getKey();
                    final var paramType = entry.getValue().getType();
                    final var paramNode = paramNodes.get(paramName);
                    return setup.getMapper().convertValue(paramNode, paramType);
                })
                .toList();
    }

    /**
     * Convert tool response to string to send to LLM. For void return type a fixed success string is sent to LLM.
     *
     * @param tool   Tool being called, we use this to derive the return type
     * @param result Actual result from the tool
     * @return JSON serialized result
     */
    @SneakyThrows
    private String toStringContent(CallableTool tool, Object result) {
        if (tool.getReturnType().equals(Void.TYPE)) {
            return "success"; //This is recommended by OpenAI
        }
        else {
            if (tool.getReturnType().isAssignableFrom(String.class) || Primitives.isWrapperType(tool.getReturnType())) {
                return Objects.toString(result);
            }
        }
        return setup.getMapper().writeValueAsString(result);
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
        if (object.getClass().isAssignableFrom(String.class) || Primitives.isWrapperType(object.getClass())) {
            return xmlMapper.createObjectNode().put("data", Objects.toString(object));
        }
        return xmlMapper.valueToTree(object);
    }
}
