package configuredagents;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A sentinel agent extension that provides a registry of agents that can be dynamically configured and spun up.
 * Agents are exposed to the calling LLM as facts and can be invoked by the top level agent according to the
 * requirement.
 */
@Slf4j
public class AgentRegistry<R, T, A extends Agent<R, T, A>> implements AgentExtension<R,T,A> {

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

    private final SimpleCache<ConfiguredAgent> agentCache;


    @Builder
    public AgentRegistry(
            @NonNull AgentConfigurationSource agentSource,
            @NonNull Function<AgentMetadata, ConfiguredAgent> agentFactory,
            final Predicate<AgentMessage> parentMessageFilter,
            final ObjectMapper mapper) {
        this.agentSource = agentSource;
        this.parentMessageFilter = Objects.requireNonNullElseGet(parentMessageFilter, () -> message -> false);
        this.mapper = Objects.requireNonNullElseGet(mapper, JsonUtils::createMapper);
        this.agentCache = new SimpleCache<>(agentId -> {
            log.info("Building new agent for: {}", agentId);
            return agentFactory.apply(
                    agentSource.read(agentId)
                            .orElseThrow(() -> agentNotFoundError(agentId)));
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
        if(log.isDebugEnabled()) {
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
                .map(metadata -> new ExposedAgentMetadata(metadata.getId(),
                                                          metadata.getConfiguration().getAgentName(),
                                                          metadata.getConfiguration().getDescription(),
                                                          metadata.getConfiguration().getInputSchema(),
                                                          metadata.getConfiguration().getOutputSchema()))
                .orElseThrow(() -> agentNotFoundError(agentId));
    }

    @Tool("Invoke an agent with input in the schema as defined in the agent metadata")
    public AgentExecutionResult invokeAgent(
            AgentRunContext<JsonNode> context,
            @JsonPropertyDescription("ID of the agent to be invoked") String agentId,
            @JsonPropertyDescription("The json serialized structured input to be sent to the agent") String agentInput) {
        try {
            final var parentMessages = context.getOldMessages();
            final var messagesToBeSent = new ArrayList<>(parentMessages.stream()
                                                                 .filter(parentMessageFilter)
                                                                 .toList());
            final var agent = agentCache.find(agentId).orElse(null);
            if (null == agent) {
                log.error("Agent not found: {}", agentId);
                return agentNotFound(context, agentId);
            }
            final var response = agent.executeAsync(AgentInput.<JsonNode>builder()
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
                    "Error running agent %s: [%s] %s".formatted(agentId,
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
    public  List<FactList> facts(R request, AgentRunContext<R> context, A agent) {
        return List.of(new FactList(
                "List of agents registered in the system and can be invoked",
                availableAgents()));
    }

    @Override
    public  ExtensionPromptSchema additionalSystemPrompts(
            R request,
            AgentRunContext<R> context,
            A agent,
            ProcessingMode processingMode) {
        return new ExtensionPromptSchema(
                List.of(SystemPrompt.Task.builder()
                                .objective(
                                        """
                                                Offload complex tasks to other agents if available.
                                                """)
                                .instructions(
                                        """
                                                   The list of available agents is provided in the facts.
                                                    You MUST invoke the agent_registry_get_agent_metadata tool to understand the agent's capabilities and input/output schema.
                                                    Once understood, you can invoke an agent using the `agent_registry_invoke_agent` tool with the agent ID and input.
                                                """)
                                .build()

                       ),
                List.of());
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        return Optional.empty();
    }

    @Override
    public  void consume(JsonNode output, A agent) {
        //Nothing to do here
    }

    @Override
    public  void onExtensionRegistrationCompleted(A agent) {
        //Nothing to do here
    }

    private static IllegalArgumentException agentNotFoundError(String agentId) {
        return new IllegalArgumentException("Agent not found: " + agentId);
    }

    private AgentExecutionResult agentNotFound(AgentRunContext<JsonNode> context, String agentId) {
        final var errorNode = context.getAgentSetup()
                .getMapper()
                .createObjectNode()
                .put("message", "Agent not found: " + agentId)
                .set("availableAgents", context.getAgentSetup()
                        .getMapper()
                        .valueToTree(availableAgents()));
        return AgentExecutionResult.fail(errorNode);
    }

    private AgentExecutionResult fail(AgentRunContext<JsonNode> context, String errorMessage) {
        final var errorNode = context.getAgentSetup()
                .getMapper()
                .createObjectNode()
                .put("message", errorMessage);
        return AgentExecutionResult.fail(errorNode);
    }

    @NotNull
    private List<Fact> availableAgents() {
        return agentSource.list()
                .stream()
                .map(agentMetadata -> new Fact(
                        "Available Agent ID: %s".formatted(agentMetadata.getId()),
                        agentMetadata.getConfiguration().getDescription()))
                .toList();
    }

}
