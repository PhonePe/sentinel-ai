package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.ser.ToXmlGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.primitives.Primitives;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.InternalTool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.core.tools.ToolRunApprovalSeeker;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;
import io.appform.signals.signals.ConsumingFireForgetSignal;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    @VisibleForTesting
    static final String OUTPUT_VARIABLE_NAME = "output";

    @Value
    public static class ProcessingCompletedData<R, T, A extends Agent<R, T, A>> {
        A agent;
        AgentSetup agentSetup;
        AgentRunContext<R> context;
        AgentInput<R> input;
        AgentOutput<?> output;
        ProcessingMode processingMode;
    }

    private final Class<T> outputType;
    private final String systemPrompt;
    @Getter
    private final AgentSetup setup;
    private final List<AgentExtension<R, T, A>> extensions;
    private final ToolRunApprovalSeeker<R, T, A> toolRunApprovalSeeker;
    private final Map<String, ExecutableTool> knownTools = new ConcurrentHashMap<>();
    private final XmlMapper xmlMapper = new XmlMapper();
    private final ConsumingFireForgetSignal<ProcessingCompletedData<R, T, A>> requestCompleted =
            new ConsumingFireForgetSignal<>();

    @SuppressWarnings("unchecked")
    private final A self = (A) this;

    protected Agent(
            @NonNull Class<T> outputType,
            @NonNull String systemPrompt,
            @NonNull AgentSetup setup,
            List<AgentExtension<R, T, A>> extensions,
            Map<String, ExecutableTool> knownTools) {
        this(outputType, systemPrompt, setup, extensions, knownTools, new ApproveAllToolRuns<>());
    }

    @SneakyThrows
    protected Agent(
            @NonNull Class<T> outputType,
            @NonNull String systemPrompt,
            @NonNull AgentSetup setup,
            List<AgentExtension<R, T, A>> extensions,
            Map<String, ExecutableTool> knownTools,
            ToolRunApprovalSeeker<R, T, A> toolRunApprovalSeeker) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(systemPrompt), "Please provide a valid system prompt");

        this.outputType = outputType;
        this.systemPrompt = systemPrompt;
        this.setup = setup;
        this.extensions = Objects.requireNonNullElseGet(extensions, List::of);
        this.toolRunApprovalSeeker = Objects.requireNonNullElseGet(toolRunApprovalSeeker, ApproveAllToolRuns::new);
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

    public ConsumingFireForgetSignal<ProcessingCompletedData<R, T, A>> onRequestCompleted() {
        return requestCompleted;
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
        return registerTools(Objects.requireNonNullElseGet(tools, List::<InternalTool>of)
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
        final var tools = new HashMap<>(Objects.requireNonNullElseGet(callableTools, Map::of));
        if (!tools.isEmpty()) {
            log.info("Discovered tools: {}", tools.keySet());
        }
        else {
            log.debug("No tools registered");
        }
        this.knownTools.putAll(tools);
        return self;
    }

    public final AgentOutput<T> execute(final AgentInput<R> request) {
        return executeAsync(request).join();
    }

    /**
     * Execute the agent synchronously.
     *
     * @param input Input to the agent
     * @return The response from the agent
     */
    public final CompletableFuture<AgentOutput<T>> executeAsync(@NonNull AgentInput<R> input) {
        final var mergedAgentSetup = AgentUtils.mergeAgentSetup(input.getAgentSetup(), this.setup);
        final var messages = new ArrayList<>(Objects.requireNonNullElse(input.getOldMessages(), List.of()));
        final var runId = UUID.randomUUID().toString();
        final var requestMetadata = input.getRequestMetadata();
        final var facts = input.getFacts();
        final var inputRequest = input.getRequest();
        final var modelUsageStats = new ModelUsageStats();
        final var context = new AgentRunContext<>(runId,
                                                  inputRequest,
                                                  Objects.requireNonNullElseGet(
                                                          requestMetadata,
                                                          AgentRequestMetadata::new),
                                                  mergedAgentSetup,
                                                  messages,
                                                  modelUsageStats,
                                                  ProcessingMode.DIRECT);
        var finalSystemPrompt = "";
        try {
            finalSystemPrompt = systemPrompt(context, facts);
        }
        catch (JsonProcessingException e) {
            log.error("Error serializing system prompt", e);
            return CompletableFuture.completedFuture(AgentOutput.error(messages,
                                                                       context.getModelUsageStats(),
                                                                       SentinelError.error(ErrorType.SERIALIZATION_ERROR,
                                                                                           e)));
        }
        messages.add(new com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt(finalSystemPrompt,
                                                                                         false,
                                                                                         null));
        messages.add(new UserPrompt(toXmlContent(inputRequest), LocalDateTime.now()));
        final var modelRunContext = new ModelRunContext(name(),
                                                        runId,
                                                        AgentUtils.sessionId(context),
                                                        AgentUtils.userId(context),
                                                        mergedAgentSetup,
                                                        modelUsageStats,
                                                        ProcessingMode.DIRECT);
        final var processingMode = ProcessingMode.DIRECT;
        final var outputDefinitions = new ArrayList<>(extensions.stream()
                                                              .map(extension -> extension.outputSchema(processingMode))
                                                              .filter(Optional::isPresent)
                                                              .map(Optional::get)
                                                              .toList());
        outputDefinitions.add(new ModelOutputDefinition(OUTPUT_VARIABLE_NAME,
                                                        "Output generated by the agent",
                                                        outputSchema()));
        return mergedAgentSetup.getModel()
/*                .exchangeMessages(
                        context,
                        outputSchema(),
                        knownTools,
                        new AgentToolRunner<>(self,
                                              mergedAgentSetup,
                                              toolRunApprovalSeeker,
                                              context),
                        this.extensions,
                        self)
                .thenApply(modelOutput -> convertToAgentOutput(modelOutput, mergedAgentSetup))*/
                .process(modelRunContext,
                         outputDefinitions,
                         messages,
                         knownTools,
                         new AgentToolRunner<>(self,
                                               mergedAgentSetup,
                                               toolRunApprovalSeeker,
                                               context))
                .thenApply(modelOutput -> processModelOutput(modelOutput, mergedAgentSetup))
                .thenApplyAsync(response -> {
                    if (null != response.getUsage() && requestMetadata != null && requestMetadata.getUsageStats() != null) {
                        requestMetadata.getUsageStats().merge(response.getUsage());
                    }
                    requestCompleted.dispatch(new ProcessingCompletedData<>(self,
                                                                            mergedAgentSetup,
                                                                            context,
                                                                            input,
                                                                            response,
                                                                            ProcessingMode.DIRECT));
                    return response;
                });
    }

    /**
     * Streaming execution. This should be used for text streaming applications like chat etc.
     *
     * @param input The input to the agent
     * @return The response to be consumed by the client
     */
    public final CompletableFuture<AgentOutput<T>> executeAsyncStreaming(
            AgentInput<R> input,
            Consumer<byte[]> streamHandler) {
        final var mergedAgentSetup = AgentUtils.mergeAgentSetup(input.getAgentSetup(), this.setup);
        final var messages = new ArrayList<>(Objects.requireNonNullElse(input.getOldMessages(), List.<AgentMessage>of())
                                                     .stream()
                                                     .filter(message -> !message.getMessageType()
                                                             .equals(AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE))
                                                     .toList());
        final var runId = UUID.randomUUID().toString();
        final var requestMetadata = input.getRequestMetadata();
        final var request = input.getRequest();
        final var facts = input.getFacts();
        final var context = new AgentRunContext<>(runId,
                                                  request,
                                                  Objects.requireNonNullElseGet(
                                                          requestMetadata,
                                                          AgentRequestMetadata::new),
                                                  mergedAgentSetup,
                                                  messages,
                                                  new ModelUsageStats(),
                                                  ProcessingMode.STREAMING);
        var finalSystemPrompt = "";
        try {
            finalSystemPrompt = systemPrompt(context, facts);
        }
        catch (JsonProcessingException e) {
            log.error("Error serializing system prompt", e);
            return CompletableFuture.completedFuture(AgentOutput.error(messages,
                                                                       context.getModelUsageStats(),
                                                                       SentinelError.error(ErrorType.SERIALIZATION_ERROR,
                                                                                           e)));
        }
        messages.add(new com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt(finalSystemPrompt,
                                                                                         false,
                                                                                         null));
        messages.add(new UserPrompt(toXmlContent(request), LocalDateTime.now()));
        return mergedAgentSetup.getModel()
                .exchangeMessagesStreaming(
                        context,
                        knownTools,
                        new AgentToolRunner<>(self,
                                              mergedAgentSetup,
                                              toolRunApprovalSeeker,
                                              context),
                        this.extensions,
                        self,
                        streamHandler)
                .thenApply(modelOutput -> convertToAgentOutput(modelOutput, mergedAgentSetup))
                .thenApply(response -> {
                    if (null != response.getUsage() && requestMetadata != null && requestMetadata.getUsageStats() != null) {
                        requestMetadata.getUsageStats().merge(response.getUsage());
                    }
                    requestCompleted.dispatch(new ProcessingCompletedData<>(self,
                                                                            mergedAgentSetup,
                                                                            context,
                                                                            input,
                                                                            response,
                                                                            ProcessingMode.STREAMING));
                    return response;
                });
    }

    protected JsonNode outputSchema() {
        return schema(outputType);
    }

    protected T translateData(JsonNode output, AgentSetup agentSetup) throws JsonProcessingException {
        return agentSetup.getMapper().treeToValue(output, outputType);
    }

    private AgentOutput<T> convertToAgentOutput(
            ModelOutput modelOutput,
            AgentSetup mergedAgentSetup) {
        try {
            return new AgentOutput<>(null != modelOutput.getData()
                                     ? translateData(modelOutput.getData(), mergedAgentSetup)
                                     : null,
                                     modelOutput.getNewMessages(),
                                     modelOutput.getAllMessages(),
                                     modelOutput.getUsage(),
                                     modelOutput.getError());
        }
        catch (JsonProcessingException e) {
            log.error("Error converting model output to agent output. Error: {}", AgentUtils.rootCause(e), e);
            return AgentOutput.error(modelOutput.getAllMessages(),
                                     modelOutput.getUsage(),
                                     SentinelError.error(ErrorType.JSON_ERROR, e));
        }
    }

    private AgentOutput<T> processModelOutput(
            ModelOutput modelOutput,
            AgentSetup mergedAgentSetup) {
        try {
            if (modelOutput.getError() != null
                    && !modelOutput.getError().getErrorType().equals(ErrorType.SUCCESS)) {
                log.error("Error returned in model run: {}", modelOutput.getError().getMessage());
                return AgentOutput.error(
                        modelOutput.getNewMessages(),
                        modelOutput.getAllMessages(),
                        modelOutput.getUsage(),
                        modelOutput.getError());
            }
            //Creating an empty object here as we don't want to waste time doing null checks
            final var data = Objects.requireNonNullElseGet(modelOutput.getData(),
                                                           () -> setup.getMapper().createObjectNode());
            extensions.forEach(extension -> {
                final var outputDefinition = extension.outputSchema(ProcessingMode.DIRECT);
                final var outputName = outputDefinition
                        .map(ModelOutputDefinition::getName)
                        .orElse(null);
                if (outputDefinition.isEmpty() || Strings.isNullOrEmpty(outputName)) {
                    log.error("Empty output name found. Definition: {}", outputDefinition);
                    return;
                }
                final var extensionOutputData = data.get(outputName);
                if (!exists(extensionOutputData)) {
                    log.warn("No output from model for extension data named: {}", outputName);
                    return;
                }
                try {
                    extension.consume(extensionOutputData, self);
                }
                catch (Exception e) {
                    log.error("Error processing model output by extension {}: {}",
                              extension.name(), AgentUtils.rootCause(e).getMessage());
                }
            });
            final var agentOutputData = data.get(OUTPUT_VARIABLE_NAME);
            if (!exists(agentOutputData)) {
                log.warn("No output data found in model output. Returning empty agent output.");
                return AgentOutput.error(
                        modelOutput.getNewMessages(),
                        modelOutput.getAllMessages(),
                        modelOutput.getUsage(),
                        SentinelError.error(ErrorType.NO_RESPONSE,
                                            "Did not get output from model"));
            }
            return AgentOutput.success(translateData(agentOutputData, mergedAgentSetup),
                                       modelOutput.getNewMessages(),
                                       modelOutput.getAllMessages(),
                                       modelOutput.getUsage());
        }
        catch (JsonProcessingException e) {
            log.error("Error converting model output to agent output. Error: {}", AgentUtils.rootCause(e), e);
            return AgentOutput.error(
                    modelOutput.getNewMessages(),
                    modelOutput.getAllMessages(),
                    modelOutput.getUsage(),
                    SentinelError.error(ErrorType.JSON_ERROR, e));
        }
    }

    private boolean exists(final JsonNode node) {
        return node != null
                && !node.isNull()
                && !(node.isArray() && node.isEmpty());
    }

    private String systemPrompt(
            AgentRunContext<R> context,
            List<FactList> facts
                               ) throws JsonProcessingException {
        final var secondaryTasks = this.extensions
                .stream()
                .flatMap(extension -> extension
                        .additionalSystemPrompts(context.getRequest(), context, self, context.getProcessingMode())
                        .getTask()
                        .stream())
                .toList();
        final var knowledgeFromExtensions = this.extensions
                .stream()
                .flatMap(extension -> extension.facts(context.getRequest(), context, self).stream())
                .toList();
        final var knowledge = new ArrayList<>(knowledgeFromExtensions);
        knowledge.addAll(Objects.requireNonNullElseGet(facts, List::of));
        final var prompt = new SystemPrompt()
                .setName(name())
                .setCoreInstructions(
                        "Your main job is to answer the user query as provided in user prompt in the `user_input` tag. "
                                + (!context.getOldMessages().isEmpty()
                                   ? "Use the provided old messages for extra context and information. " : "")
                                + ((!secondaryTasks.isEmpty())
                                   ? "Perform the provided secondary tasks as well and populate the output in " +
                                           "designated output field for the task. "
                                   : "")
                                + ((!knowledge.isEmpty())
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
                                             .setCustomParams(context.getRequestMetadata().getCustomParams()));
        }
        final var generatedSystemPrompt = xmlMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prompt);
        log.debug("Final system prompt: {}", generatedSystemPrompt);
        return generatedSystemPrompt;

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
