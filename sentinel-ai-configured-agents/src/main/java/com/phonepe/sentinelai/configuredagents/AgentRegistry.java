package com.phonepe.sentinelai.configuredagents;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * A sentinel agent extension that provides a registry of agents that can be dynamically configured and spun up.
 * Agents are exposed to the calling LLM as facts and can be invoked by the top level agent according to the
 * requirement.
 */
@Slf4j
public class AgentRegistry<R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {

    private static final AgentMetadataAccessMode DEFAULT_METADATA_ACCESS_MODE =
            AgentMetadataAccessMode.INCLUDE_IN_PROMPT;
    /**
     * Storage for agent configurations.
     */
    @NonNull
    private final AgentConfigurationSource agentSource;

    /**
     * By default, the registry will not pass parent agent messages to the invoked agent. If this option is set,
     * registry will use the provided predicate to filter parent messages and pass them to the invoked agent.
     */
    private final Predicate<AgentMessage> parentMessageFilter;

    private final ObjectMapper mapper;

    private final AgentMetadataAccessMode agentMetadataAccessMode;

    private final SimpleCache<ConfiguredAgent> agentCache;

    private A agent;

    @Builder
    public AgentRegistry(
            @NonNull AgentConfigurationSource agentSource,
            @NonNull BiFunction<AgentMetadata, A, ConfiguredAgent> agentFactory,
            final Predicate<AgentMessage> parentMessageFilter,
            final ObjectMapper mapper,
            final AgentMetadataAccessMode agentMetadataAccessMode) {
        this.agentSource = agentSource;
        this.parentMessageFilter = Objects.requireNonNullElseGet(parentMessageFilter, () -> message -> false);
        this.mapper = Objects.requireNonNullElseGet(mapper, JsonUtils::createMapper);
        this.agentMetadataAccessMode = Objects.requireNonNullElse(agentMetadataAccessMode,
                                                                  DEFAULT_METADATA_ACCESS_MODE);
        this.agentCache = new SimpleCache<>(agentId -> {
            log.info("Building new agent for: {}", agentId);
            return agentFactory.apply(
                    agentSource.read(agentId)
                            .orElse(null), this.agent);
        });
    }

    @SneakyThrows
    public List<AgentMetadata> loadAgentsFromFile(final String agentConfig) {
        return loadAgentsFromContent(Files.readAllBytes(Paths.get(agentConfig)));
    }

    @SneakyThrows
    public List<AgentMetadata> loadAgentsFromContent(byte[] content) {
        final var configs = mapper.readValue(content, new TypeReference<List<AgentConfiguration>>() {
        });
        final var agents = configs.stream()
                .map(this::configureAgent)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (log.isDebugEnabled()) {
            log.debug("Loaded agents: {}", agents.stream().map(AgentMetadata::getId).toList());
        }
        return agents;
    }

    public Optional<AgentMetadata> configureAgent(@NonNull final AgentConfiguration configuration) {
        final var fixedConfig = new AgentConfiguration(
                configuration.getAgentName(),
                configuration.getDescription(),
                configuration.getPrompt(),
                Objects.requireNonNullElseGet(configuration.getInputSchema(),
                                              () -> JsonUtils.schemaForPrimitive(String.class, "data", mapper)),
                Objects.requireNonNullElseGet(configuration.getOutputSchema(),
                                              () -> JsonUtils.schema(String.class)),
                Objects.requireNonNullElseGet(configuration.getCapabilities(), List::of));
        final var agentId = AgentUtils.id(fixedConfig.getAgentName());
        return agentSource.save(agentId, fixedConfig);
    }

    @Tool("Get agent metadata. Use this to get agent id, name, description, input and output schema etc")
    public ExposedAgentMetadata getAgentMetadata(
            @JsonPropertyDescription("ID of the agent to get metadata for") String agentId) {
        return agentSource.read(agentId)
                .map(AgentRegistry::convertToExposedMetadata)
                .orElseThrow(() -> agentNotFoundError(agentId));
    }

    @Tool("Invoke an agent with input in the schema as defined in the agent metadata")
    public AgentExecutionResult invokeAgent(
            AgentRunContext<JsonNode> context,
            @JsonPropertyDescription("ID of the agent to be invoked") String agentId,
            @JsonPropertyDescription("The json serialized structured input to be sent to the agent") String agentInput) {
        final var configuredAgent = agentCache.find(agentId)
                .orElseThrow(() -> agentNotFoundError(agentId));
        final var parentMessages = context.getOldMessages();
        final var messagesToBeSent = new ArrayList<>(parentMessages.stream()
                                                             .filter(parentMessageFilter)
                                                             .toList());

        try {
            final var response = configuredAgent.executeAsync(AgentInput.<JsonNode>builder()
                                                                      .request(context.getAgentSetup()
                                                                                       .getMapper()
                                                                                       .readTree(agentInput))
                                                                      .requestMetadata(context.getRequestMetadata())
                                                                      .agentSetup(context.getAgentSetup())
                                                                      .oldMessages(messagesToBeSent)
                                                                      .build())
                    .join();
            if (response.getData() != null) {
                return AgentExecutionResult.success(response.getData());
            }
            return fail(context,
                        "Error running agent %s: [%s] %s".formatted(
                                agentId,
                                response.getError().getErrorType(),
                                response.getError().getMessage()));
        }
        catch (Exception e) {
            log.error("Error invoking agent: {}", agentId, e);
            return fail(context,
                        "Error running agent %s: %s".formatted(agentId, AgentUtils.rootCause(e).getMessage()));
        }
    }

    @Override
    public String name() {
        return "agent-registry";
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        final var tools = ToolUtils.readTools(this);
        if (agentMetadataAccessMode.equals(AgentMetadataAccessMode.INCLUDE_IN_PROMPT)) {
            log.debug("Removing metadata lookup tool as metadata is included in facts");
            tools.remove("agent_registry_get_agent_metadata");
        }
        return tools;
    }

    @Override
    public List<FactList> facts(R request, AgentRunContext<R> context, A agent) {
        return List.of(new FactList(
                "List of agents registered in the system and can be invoked",
                agentSource.list()
                        .stream()
                        .map(agentMetadata -> switch (agentMetadataAccessMode) {
                            case INCLUDE_IN_PROMPT -> new Fact(
                                    "Available Agent: %s".formatted(agentMetadata.getConfiguration().getAgentName()),
                                    "Agent Metadata for invoking agent: %s".formatted(
                                            convertToExposedMetadataJson(agentMetadata)));
                            case METADATA_TOOL_LOOKUP -> new Fact(
                                    "Available Agent: %s".formatted(agentMetadata.getConfiguration().getAgentName()),
                                    "Agent Metadata for invoking agent: %s".formatted(agentMetadata.getId()));
                        })
                        .toList()));
    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(
            R request,
            AgentRunContext<R> context,
            A agent,
            ProcessingMode processingMode) {
        final var promptForAgentInvocation
                = switch (agentMetadataAccessMode) {
            case INCLUDE_IN_PROMPT -> """
                       Each agent's metadata is provided in the facts. Use this to understand the agent's
                        capabilities, input and output schema.
                    """;
            case METADATA_TOOL_LOOKUP -> """
                    You MUST invoke the agent_registry_get_agent_metadata tool to understand the agent's
                     capabilities and input/output schema.
                    """;
        };
        return new ExtensionPromptSchema(
                List.of(SystemPrompt.Task.builder()
                                .objective(
                                        """
                                                    Offload complex tasks to other agents if available.
                                                """)
                                .instructions(
                                        """
                                                   The list of available agents is provided in the facts.
                                                    %s
                                                    Once understood, you can invoke an agent using the `agent_registry_invoke_agent` tool with the agent ID and input.
                                                    Invocation response has the following fields:
                                                    - successful: boolean indicating if the agent invocation was successful
                                                    - agentOutput: json serialized structured output from the agent (present only if successful = true)
                                                    - error: reason for failure (present only if successful = false)
                                                    ALWAYS FOLLOW THESE INSTRUCTIONS:
                                                    - DO NOT INVOKE AGENT WITHOUT UNDERSTANDING ITS CAPABILITIES AND INPUT/OUTPUT SCHEMA.
                                                    - DO NOT MAKE ASSUMPTIONS ABOUT THE FUNCTIONALITY OF THE INVOKED AGENT.
                                                    - DO NOT try to mimic the functionality of the invoked agent yourself.
                                                    - It is ok to fail the task if no suitable agent is found or agent invocation fails.
                                                """.formatted(promptForAgentInvocation))
                                .build()
                       ),
                List.of());
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        return Optional.empty();
    }

    @Override
    public void consume(JsonNode output, A agent) {
        //Nothing to do here
    }

    @Override
    public void onExtensionRegistrationCompleted(A agent) {
        this.agent = agent;
    }

    private static IllegalArgumentException agentNotFoundError(String agentId) {
        return new IllegalArgumentException("Agent not found: " + agentId);
    }

    @SneakyThrows
    private String convertToExposedMetadataJson(AgentMetadata agentMetadata) {
        return mapper.writeValueAsString(convertToExposedMetadata(agentMetadata));
    }

    private static ExposedAgentMetadata convertToExposedMetadata(AgentMetadata metadata) {
        return new ExposedAgentMetadata(metadata.getId(),
                                        metadata.getConfiguration().getAgentName(),
                                        metadata.getConfiguration().getDescription(),
                                        metadata.getConfiguration().getInputSchema(),
                                        metadata.getConfiguration().getOutputSchema());
    }

    private AgentExecutionResult fail(AgentRunContext<JsonNode> context, String errorMessage) {
        return AgentExecutionResult.fail(context.getAgentSetup()
                                                 .getMapper()
                                                 .createObjectNode()
                                                 .textNode(errorMessage));
    }
}
