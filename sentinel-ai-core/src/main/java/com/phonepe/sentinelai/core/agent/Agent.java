package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
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
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.*;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;
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
        ToolCallResponse runTool(AgentRunContext<S> context, Map<String, ExecutableTool> tool, ToolCall toolCall);
    }

    private final Class<T> outputType;
    private final String systemPrompt;
    private final AgentSetup setup;
    private final List<AgentExtension> extensions;
    private final Map<String, ExecutableTool> knownTools = new ConcurrentHashMap<>();
    private final XmlMapper xmlMapper = new XmlMapper();

    @SneakyThrows
    protected Agent(
            @NonNull Class<T> outputType,
            @NonNull String systemPrompt,
            @NonNull AgentSetup setup,
            List<AgentExtension> extensions,
            Map<String, ExecutableTool> knownTools) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(systemPrompt), "Please provide a valid system prompt");

        this.outputType = outputType;
        this.systemPrompt = systemPrompt;
        this.setup = setup;
        this.extensions = Objects.requireNonNullElseGet(extensions, List::of);
        xmlMapper.registerModule(new JavaTimeModule());
        xmlMapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        xmlMapper.configure(ToXmlGenerator.Feature.WRITE_XML_1_1, true);
        xmlMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        xmlMapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);
        registerTools(ToolUtils.readTools(this));
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
     * @param tools List of callable tools
     * @return this
     */
    public A registerTools(List<ExecutableTool> tools) {
        return registerTools(Objects.requireNonNullElseGet(tools, List::<InternalTool>of)
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
    public A registerTools(Map<String, ExecutableTool> callableTools) {
        final var tools = new HashMap<>(Objects.requireNonNullElseGet(callableTools, Map::of));
        if (!tools.isEmpty()) {
            log.info("Discovered tools: {}", tools.keySet());
        }
        else {
            log.debug("No tools registered");
        }
        this.knownTools.putAll(tools);
        return (A) this;
    }

    public final AgentOutput<T> execute(final AgentInput<R> request) {
        return executeAsync(request).join();
    }

    /**
     * Execute the agent synchronously.
     *
     * @param input Input to the agent
     * @return
     */
    @SuppressWarnings("unchecked")
    public final CompletableFuture<AgentOutput<T>> executeAsync(@NonNull AgentInput<R> input) {
        final var mergedAgentSetup = mergeAgentSetup(input.getAgentSetup(), this.setup);
        final var messages = new ArrayList<>(Objects.requireNonNullElse(input.getOldMessages(), List.of()));
        final var runId = UUID.randomUUID().toString();
        final var requestMetadata = input.getRequestMetadata();
        final var facts = input.getFacts();
        final var inputRequest = input.getRequest();
        final var context = new AgentRunContext<>(runId,
                                                  inputRequest,
                                                  requestMetadata,
                                                  mergedAgentSetup,
                                                  messages,
                                                  new ModelUsageStats());
        var finalSystemPrompt = "";
        try {
            finalSystemPrompt = systemPrompt(inputRequest, facts, requestMetadata, messages);
        }
        catch (JsonProcessingException e) {
            log.error("Error serializing system prompt", e);
            return CompletableFuture.completedFuture(AgentOutput.error(messages,
                                                                       context.getModelUsageStats(),
                                                                       SentinelError.error(ErrorType.SERIALIZATION_ERROR,
                                                                                           e)));
        }
        log.debug("Final system prompt: {}", finalSystemPrompt);
        messages.add(new com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt(finalSystemPrompt,
                                                                                         false,
                                                                                         null));
        messages.add(new UserPrompt(toXmlContent(inputRequest), LocalDateTime.now()));
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
     * @param input The input to the agent
     * @return The response to be consumed by the client
     */
    @SuppressWarnings("unchecked")
    public final CompletableFuture<AgentOutput<byte[]>> executeAsyncStreaming(
            AgentInput<R> input,
            Consumer<byte[]> streamHandler) {
        final var mergedAgentSetup = mergeAgentSetup(input.getAgentSetup(), this.setup);
        final var messages = new ArrayList<>(Objects.requireNonNullElse(input.getOldMessages(), List.<AgentMessage>of())
                                                     .stream()
                                                     .filter(message -> !message.getMessageType()
                                                             .equals(AgentMessageType.SYSTEM_PROMPT_REQUEST))
                                                     .toList());
        final var runId = UUID.randomUUID().toString();
        final var requestMetadata = input.getRequestMetadata();
        final var request = input.getRequest();
        final var facts = input.getFacts();
        final var context = new AgentRunContext<>(runId,
                                                  request,
                                                  requestMetadata,
                                                  mergedAgentSetup,
                                                  messages,
                                                  new ModelUsageStats());
        var finalSystemPrompt = "";
        try {
            finalSystemPrompt = systemPrompt(request, facts, requestMetadata, messages);
        }
        catch (JsonProcessingException e) {
            log.error("Error serializing system prompt", e);
            return CompletableFuture.completedFuture(AgentOutput.error(messages,
                                                                       context.getModelUsageStats(),
                                                                       SentinelError.error(ErrorType.SERIALIZATION_ERROR,
                                                                                           e)));
        }
        log.debug("Final system prompt: {}", finalSystemPrompt);
        messages.add(new com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt(finalSystemPrompt,
                                                                                         false,
                                                                                         null));
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
            List<FactList> facts,
            final AgentRequestMetadata requestMetadata,
            List<AgentMessage> messages) throws JsonProcessingException {
        final var secondaryTasks = this.extensions
                .stream()
                .map(extension -> new SystemPrompt.SecondaryTask()
                        .setInstructions(extension.additionalSystemPrompts(request,
                                                                           requestMetadata,
                                                                           (A) this)))
                .toList();
        final var knowledgeFromExtensions = this.extensions
                .stream()
                .flatMap(extension -> extension.facts(request, requestMetadata, (A) this).stream())
                .toList();
        final var knowledge = new ArrayList<FactList>();
        if (null != facts && !facts.isEmpty()) {
            knowledge.addAll(facts);
        }
        knowledge.addAll(knowledgeFromExtensions);
        final var prompt = new SystemPrompt()
                .setCoreInstructions(
                        "Your main job is to answer the user query as provided in user prompt in the `user_input` tag. "
                                + (!messages.isEmpty()
                                   ? "Use the provided old messages for extra context and information. " : "")
                                + ((!secondaryTasks.isEmpty())
                                   ? "Perform the provided secondary tasks as well and populate the output in " +
                                           "designated output field for the task. "
                                   : "")
                                + ((!knowledge.isEmpty())
                                   ? "Use the provided knowledge and facts to enrich your responses."
                                   : ""))
                .setPrimaryTask(new SystemPrompt.PrimaryTask()
                                        .setRole(systemPrompt)
                                        .setTool(this.knownTools.values()
                                                         .stream()
                                                         .map(tool -> SystemPrompt.ToolSummary.builder()
                                                                 .name(tool.getToolDefinition().getName())
                                                                 .description(tool.getToolDefinition()
                                                                                      .getDescription())
                                                                 .build())
                                                         .toList()))
                .setSecondaryTask(secondaryTasks)
                .setFacts(knowledge);
        if (null != requestMetadata) {
            prompt.setAdditionalData(new SystemPrompt.AdditionalData()
                                             .setSessionId(requestMetadata.getSessionId())
                                             .setUserId(requestMetadata.getUserId())
                                             .setCustomParams(requestMetadata.getCustomParams()));
        }
        return xmlMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(prompt);

    }

    private static AgentSetup mergeAgentSetup(final AgentSetup lhs, final AgentSetup rhs) {
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
            Map<String, ExecutableTool> tools,
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
                                                        response.getErrorType(),
                                                        response.getResponse(),
                                                        Duration.ofMillis(stopwatch.elapsed(TimeUnit.MILLISECONDS))));
        return response;
    }

    @SuppressWarnings("java:S3011")
    private ToolCallResponse runTool(
            AgentRunContext<R> context,
            Map<String, ExecutableTool> tools,
            ToolCall toolCall) {
        //TODO::RETRY LOGIC
        final var tool = tools.get(toolCall.getToolName());
        if (null == tool) {
            return new ToolCallResponse(toolCall.getToolCallId(),
                                        toolCall.getToolName(),
                                        ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                        "Tool call failed. Invalid tool: %s".

                                                formatted(toolCall.getToolName()),
                                        LocalDateTime.now());
        }
        return tool.accept(new ExecutableToolVisitor<>() {
            @Override
            public ToolCallResponse visit(ExternalTool externalTool) {
                final var response = externalTool.getCallable()
                        .apply(toolCall.getToolName(), toolCall.getArguments());
                final var error = response.error();
                if (!error.equals(ErrorType.SUCCESS)) {
                    log.error("Error calling external tool {}: {}", toolCall.getToolName(), response.response());
                    return new ToolCallResponse(
                            toolCall.getToolCallId(),
                            toolCall.getToolName(),
                            error,
                            "Tool call failed. External tool error: %s".formatted(Objects.toString(response.response())),
                            LocalDateTime.now());
                }
                try {
                    return new ToolCallResponse(toolCall.getToolCallId(),
                                                toolCall.getToolName(),
                                                ErrorType.SUCCESS,
                                                setup.getMapper().writeValueAsString(response.response()),
                                                LocalDateTime.now());
                }
                catch (JsonProcessingException e) {
                    return new ToolCallResponse(toolCall.getToolCallId(),
                                                toolCall.getToolName(),
                                                ErrorType.SERIALIZATION_ERROR,
                                                "Error serializing external tool response: %s".
                                                        formatted(Objects.toString(response.response())),
                                                LocalDateTime.now());
                }
            }

            @Override
            public ToolCallResponse visit(InternalTool internalTool) {
                try {
                    final var args = new ArrayList<>();
                    if (internalTool.getToolDefinition().isContextAware()) {
                        args.add(context);
                    }
                    args.addAll(params(internalTool.getMethodInfo(), toolCall.getArguments()));
                    final var callable = internalTool.getMethodInfo().callable();
                    callable.setAccessible(true);
                    log.info("Calling tool: {} Tool call ID: {}", toolCall.getToolName(), toolCall.getToolCallId());
                    var resultObject = callable.invoke(internalTool.getInstance(), args.toArray());
                    return new ToolCallResponse(toolCall.getToolCallId(),
                                                toolCall.getToolName(),
                                                ErrorType.SUCCESS,
                                                toStringContent(internalTool, resultObject),
                                                LocalDateTime.now());
                }
                catch (InvocationTargetException e) {
                    log.info("Local error making tool call " + toolCall.getToolCallId(), e);
                    final var rootCause = AgentUtils.rootCause(e);
                    return new ToolCallResponse(toolCall.getToolCallId(),
                                                toolCall.getToolName(),
                                                ErrorType.TOOL_CALL_PERMANENT_FAILURE,
                                                "Tool call local failure: %s".formatted(rootCause.getMessage()),
                                                LocalDateTime.now());
                }
                catch (Exception e) {
                    log.info("Error making tool call " + toolCall.getToolCallId(), e);
                    final var rootCause = AgentUtils.rootCause(e);
                    return new ToolCallResponse(toolCall.getToolCallId(),
                                                toolCall.getToolName(),
                                                ErrorType.TOOL_CALL_TEMPORARY_FAILURE,
                                                "Tool call failed. Threw exception: %s".formatted(rootCause.getMessage()),
                                                LocalDateTime.now());
                }

            }
        });

    }

    /**
     * Convert parameters string received from LLM to actual parameters for tool call
     *
     * @param methodInfo Method information for the tool
     * @param params     Parameters string to be converted
     * @return List of parameters to be passed to the tool/function
     */
    @SneakyThrows
    private List<Object> params(ToolMethodInfo methodInfo, String params) {
        final var objectMapper = setup.getMapper();
        return ToolUtils.convertToRealParams(methodInfo, params, objectMapper);
    }

    /**
     * Convert tool response to string to send to LLM. For void return type a fixed success string is sent to LLM.
     *
     * @param tool   Tool being called, we use this to derive the return type
     * @param result Actual result from the tool
     * @return JSON serialized result
     */
    @SneakyThrows
    private String toStringContent(InternalTool tool, Object result) {
        final var returnType = tool.getMethodInfo().returnType();
        if (returnType.equals(Void.TYPE)) {
            return "success"; //This is recommended by OpenAI
        }
        else {
            if (returnType.isAssignableFrom(String.class) || Primitives.isWrapperType(returnType)) {
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
