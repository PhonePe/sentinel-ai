package configuredagents;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
@AllArgsConstructor
@Slf4j
@Builder
public class AgentRegistry implements AgentExtension {

    @NonNull
    private final HttpToolboxFactory<?> httpToolboxFactory;
    @NonNull
    private final ConfiguredAgentSource agentSource;

    private final boolean skipAgentInjectionAsFact;
    private final boolean includeParentAgentMessages;

    private final Map<String, ConfiguredAgent> agentCache = new ConcurrentHashMap<>();

    public Optional<AgentMetadata> configureAgent(
            @NonNull final AgentConfiguration configuration) {
        final var agentId = configuration.getAgentName().replaceAll("[\\s\\p{Punct}]", "_").toLowerCase();
        return agentSource.save(agentId, configuration);
    }

    //    @Tool("Get an agent based on query")
    public List<ConfiguredAgentSource.AgentSearchResponse> findAgent(
            @JsonPropertyDescription("A query to find agent " +
                    "based on requirements") String query) {
        return agentSource.find(query);
    }

    @Tool("Get agent metadata")
    public ExposedAgentMetadata getAgentMetadata(
            @JsonPropertyDescription("ID of the agent to get metadata for") String agentId) {
        return agentSource.read(agentId)
                .map(metadata -> new ExposedAgentMetadata(metadata.getId(),
                                                          metadata.getConfiguration().getAgentName(),
                                                          metadata.getConfiguration().getDescription(),
                                                          metadata.getConfiguration().getInputSchema(),
                                                          metadata.getConfiguration().getOutputSchema()))
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
    }

    @Tool("Invoke an agent with input")
    public JsonNode invokeAgent(
            AgentRunContext<JsonNode> context,
            @JsonPropertyDescription("ID of the agent to be invoked") String agentId,
            @JsonPropertyDescription("The json serialized structured input to be sent to the agent") String agentInput) {
        try {
            final var parentMessages = context.getOldMessages();
            final var messagesToBeSent = new ArrayList<AgentMessage>();
            if (includeParentAgentMessages) {
                messagesToBeSent.addAll(parentMessages);
            }
            final var response = agentCache.computeIfAbsent(
                            agentId,
                            aid -> {
                                log.info("Building new agent for: {}", aid);
                                final var agentMeta = agentSource.read(agentId)
                                        .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + aid));
                                final var agentConfiguration = agentMeta.getConfiguration();
                                final var selectedHttpTools
                                        = Objects.<Map<String, Set<String>>>requireNonNullElseGet(
                                        agentConfiguration.getSelectedRemoteHttpTools(), Map::of);
                                final var httpToolBoxes = selectedHttpTools
                                        .entrySet()
                                        .stream()
                                        .map(toolsFromUpstream -> new ComposingToolBox(
                                                List.of(httpToolboxFactory.create(
                                                                toolsFromUpstream.getKey())
                                                                .orElseThrow(
                                                                        () -> new IllegalArgumentException(
                                                                                "No HTTP tool box found" +
                                                                                        " for: " + toolsFromUpstream.getKey()))),
                                                toolsFromUpstream.getValue()))
                                        .toList();
                                return new ConfiguredAgent(
                                        agentConfiguration.getAgentName(),
                                        agentConfiguration.getDescription(),
                                        agentConfiguration.getPrompt(),
                                        List.of(), //TODO::EXTENSIONS
                                        new ComposingToolBox(httpToolBoxes, Set.of()),
                                        agentConfiguration.getInputSchema(),
                                        agentConfiguration.getOutputSchema());
                            })
                    .executeAsync(AgentInput.<JsonNode>builder()
                                          .request(context.getAgentSetup().getMapper().readTree(agentInput))
                                          .requestMetadata(context.getRequestMetadata())
                                          .agentSetup(context.getAgentSetup())
                                          .oldMessages(messagesToBeSent)
                                          .build())
                    .join();
            if (response.getData() != null) {
                return response.getData();
            }
            return context.getAgentSetup()
                    .getMapper()
                    .createObjectNode()
                    .textNode("Error running agent %s: [%s] %s ".formatted(agentId,
                                                                           response.getError().getErrorType(),
                                                                           response.getError().getMessage()));
        }
        catch (Exception e) {
            log.error("Error invoking agent: {}", agentId, e);
            return context.getAgentSetup()
                    .getMapper()
                    .createObjectNode()
                    .textNode("Error running agent %s: %s".formatted(agentId, AgentUtils.rootCause(e).getMessage()));
        }
    }

    @Override
    public String name() {
        return "agent-registry";
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> List<FactList> facts(R request, AgentRequestMetadata metadata, A agent) {
        return List.of(new FactList(
                "List of agents registered in the system and can be invoked",
                agentSource.list()
                        .stream()
                        .map(agentMetadata -> new Fact(
                                "Available Agent ID: %s".formatted(agentMetadata.getId()),
                                agentMetadata.getConfiguration().getDescription()))
                        .toList()));
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> ExtensionPromptSchema additionalSystemPrompts(
            R request,
            AgentRequestMetadata metadata,
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
    public Optional<AgentExtensionOutputDefinition> outputSchema(ProcessingMode processingMode) {
        return Optional.empty();
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> void consume(JsonNode output, A agent) {
        //Nothing to do here
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> void onRegistrationCompleted(A agent) {
        //Nothing to do here
    }
}
