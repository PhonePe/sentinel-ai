package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.primitives.Primitives;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
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
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 * Base class for all agents. Derive this to create own agents.
 *
 * @param <R> Request type
 * @param <D> dependencies for the agent
 * @param <T> Response type
 */
@Slf4j
public abstract class Agent<R, D, T, A extends Agent<R, D, T, A>> {

    @FunctionalInterface
    public interface ToolRunner {
        ToolCallResponse runTool(AgentRunContext<?, ?> context, Map<String, CallableTool> tool, ToolCall toolCall);
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
     * See {@link Agent#executeAsync(Object, AgentRequestMetadata, List, AgentSetup)} for parameters.
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
     * @param oldMessages     Old messages. List of old messages to be sent to the LLM for this run. If set to null,
     *                        messages are generated and consumed by the agent in this session.
     * @param agentSetup      Setup for the agent. This is an override at runtime. If set to null, the setup provided
     *                        will be used. Whatever fields are provided are merged with the setup provided during
     *                        agent creation. If same fields are provided in both places, the ones provided at
     *                        runtime will get precedence.
     * @return Agent output. Please see {@link AgentOutput} for details.
     */
    public final CompletableFuture<AgentOutput<T>> executeAsync(
            R request,
            AgentRequestMetadata requestMetadata,
            List<AgentMessage> oldMessages,
            AgentSetup agentSetup) {
        final var mergedAgentSetup = merge(agentSetup, this.setup);
        final var messages = new ArrayList<>(Objects.requireNonNullElse(oldMessages, List.of()));
        final var runId = UUID.randomUUID().toString();
        final var context = new AgentRunContext<D, R>(runId,
                                                      request,
                                                      requestMetadata,
                                                      mergedAgentSetup,
                                                      null,
                                                      messages,
                                                      new ModelUsageStats());
        final var prompt = new SystemPromptSchema()
                .setCoreInstructions(
                        "Your main job is to answer the user query as provided in user prompt in the `user_input` tag. "
                                    + (!messages.isEmpty() ? "Use the provided old messages for extra context and information." : ""))
                .setPrimaryTask(new SystemPromptSchema.PrimaryTask()
                                        .setPrompt(systemPrompt)
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

        final String finalSystemPrompt;
        try {
            finalSystemPrompt = xmlMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(prompt);
        }
        catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        log.info("Final system prompt: {}", finalSystemPrompt);
        messages.add(new SystemPrompt(finalSystemPrompt, false, null));
        messages.add(new UserPrompt(toXmlContent(request), LocalDateTime.now()));
        return mergedAgentSetup.getModel()
                .exchange_messages(
                        context,
                                   outputType,
                                   knownTools,
                        this::runToolObserved,
                                   this.extensions,
                                   (A) this);
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

    private <R, D> ToolCallResponse runToolObserved(
            AgentRunContext<D, R> context,
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

    private <R, D> ToolCallResponse runTool(
            AgentRunContext<D, R> context,
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
                return new ToolCallResponse(toolCall.getToolCallId(),
                                            toolCall.getToolName(),
                                            false,
                                            "Tool call local failure: %s".formatted(e.getMessage()),
                                            LocalDateTime.now());
            }
            catch (Exception e) {
                return new ToolCallResponse(toolCall.getToolCallId(),
                                            toolCall.getToolName(),
                                            false,
                                            "Tool call failed. Threw exception: %s".formatted(e.getMessage()),
                                            LocalDateTime.now());
            }
        }
        return new ToolCallResponse(toolCall.getToolCallId(),
                                    toolCall.getToolName(),
                                    false,
                                    "Tool call failed. Invalid tool: %s".formatted(toolCall.getToolName()),
                                    LocalDateTime.now());

    }

    @SneakyThrows //TODO
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
                    return jsonToObject(paramNode, paramType);
                })
                .toList();
    }

    private Object jsonToObject(JsonNode node, JavaType clazz) {
        return setup.getMapper().convertValue(node, clazz);
    }

    @SneakyThrows //TODO::Handle this better
    private String toStringContent(CallableTool tool, Object result) {
        if (tool.getReturnType().equals(Void.TYPE)) {
            return "success";
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

    private <U> JsonNode toXmlNode(U object) throws JsonProcessingException {
        if (object.getClass().isAssignableFrom(String.class) || Primitives.isWrapperType(object.getClass())) {
            return xmlMapper.createObjectNode().put("data", Objects.toString(object));
        }
        return xmlMapper.valueToTree(object);
    }

    @SneakyThrows
    private <U> String toJsonString(U object) {
        if (object.getClass().isAssignableFrom(String.class) || Primitives.isWrapperType(object.getClass())) {
            return Objects.toString(object);
        }
        return setup.getMapper().writerWithDefaultPrettyPrinter().writeValueAsString(object);
    }


}
