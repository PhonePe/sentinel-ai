package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * An extension for memory management.
 * We do this in a straight forward manner.  If anything is available, inject it into system prompt.
 * If output has memory store it. No tools are needed.
 */
@Slf4j
public class AgentMemoryExtension implements AgentExtension {
    private static final String OUTPUT_KEY = "memoryOutput";

    /**
     * Whether to save memory after session ends.
     * If true, the extension will extract memories from the session and save them in the memory store.
     */
    private final boolean saveMemoryAfterSessionEnd;
    /**
     * The memory store to use for saving and retrieving memories.
     */
    private final AgentMemoryStore memoryStore;
    /**
     * The object mapper to use for serializing and deserializing memory objects.
     */
    private final ObjectMapper objectMapper;

    /**
     * The minimum reusability score for a memory to be considered relevant.
     * Memories with a score below this value will not be saved or retrieved.
     */
    private final int minRelevantReusabilityScore;

    private final Map<String, ExecutableTool> tools;

    @Builder
    public AgentMemoryExtension(
            boolean saveMemoryAfterSessionEnd,
            @NonNull AgentMemoryStore memoryStore,
            ObjectMapper objectMapper, int minRelevantReusabilityScore) {
        this.saveMemoryAfterSessionEnd = saveMemoryAfterSessionEnd;
        this.memoryStore = memoryStore;
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, JsonUtils::createMapper);
        this.minRelevantReusabilityScore = minRelevantReusabilityScore;
        this.tools = Map.copyOf(ToolUtils.readTools(this));
    }


    @Override
    public String name() {
        return "agent-memory-extension";
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> List<FactList> facts(
            R request,
            AgentRequestMetadata metadata,
            A agent) {
        final var memories = new ArrayList<FactList>();
//        //Add relevant existing memories to the prompt
        if (!Strings.isNullOrEmpty(metadata.getUserId())) {

            final var memoriesAboutUser = memoryStore
                    .findMemoriesAboutUser(metadata.getUserId(), null, 5);
            if (!memoriesAboutUser.isEmpty()) {
                final var factList = new FactList("Memories about user", memoriesAboutUser.stream()
                        .map(agentMemory -> new Fact(agentMemory.getName(), agentMemory.getContent()))
                        .toList());
                memories.add(factList);
            }
        }
        return memories;
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> ExtensionPromptSchema additionalSystemPrompts(
            R request,
            AgentRequestMetadata metadata,
            A agent,
            ProcessingMode processingMode) {
        final var prompts = new ArrayList<SystemPrompt.Task>();

        prompts.add(SystemPrompt.Task.builder()
                            .objective("""
                                                 Before proceeding with primary task, you must check if you have any memories
                                                  related to the request using the provided tool and use them in processing
                                                  the request
                                               """)
                            .tool(tools.values()
                                          .stream()
                                          .map(tool -> SystemPrompt.ToolSummary.builder()
                                                  .name(tool.getToolDefinition().getName())
                                                  .description(tool.getToolDefinition().getDescription())
                                                  .build())
                                          .toList())
                            .build());
        if (saveMemoryAfterSessionEnd && processingMode.equals(ProcessingMode.DIRECT)) { //Structured output is not supported in streaming mode
            //Add extract prompt only if extraction is needed
            final var prompt = extractionTaskPrompt();
            prompts.add(prompt);
        }

        return new ExtensionPromptSchema(prompts, List.of());
    }

    private static SystemPrompt.Task extractionTaskPrompt() {
        return SystemPrompt.Task.builder()
                .objective("YOU MUST EXTRACT MEMORY FROM MESSAGES AND POPULATE `memoryOutput` FIELD")
                .outputField(OUTPUT_KEY)
                .instructions("""                           
                                      How to extract different memory types:
                                      - SEMANTIC: Extract fact about the user or any other subject or entity being discussed in the conversation
                                      - EPISODIC: Extract a specific event or episode from the conversation.
                                      - PROCEDURAL: Extract a procedure as a list of steps or a sequence of actions that you can use later
                                      
                                      Setting memory scope and scopeId:
                                       - AGENT: Memory that is relevant to the agent's own actions and decisions. For example, if the agent is used to query an analytics store, a relevant agent level memory would be the interpretation of a particular field in the db.
                                       - ENTITY: Memory that is relevant to the entity being interacted with by the agent. For example, if the agent is a customer service agent, this would be the memory relevant to the customer.
                                      
                                      scopeId will be set to agent name for AGENT scope and userId or relevant entity id for ENTITY scope.
                                      """)
                .additionalInstructions("""
                                                IMPORTANT INSTRUCTION FOR MEMORY EXTRACTION:
                                                - Do not include non-reusable information as memories.
                                                - Extract as many useful memories as possible
                                                - If memory seems relevant to be used across sessions and users store it at agent level instead of session or user
                                                """)
                .build();
    }

    @Override
    public Optional<AgentExtensionOutputDefinition> outputSchema(ProcessingMode processingMode) {
        if (processingMode == ProcessingMode.STREAMING) {
            log.debug("Skipping output schema for streaming mode");
            return Optional.empty();
        }
        return Optional.of(memorySchema());
    }

    @NotNull
    private static AgentExtensionOutputDefinition memorySchema() {
        return new AgentExtensionOutputDefinition(OUTPUT_KEY,
                                                  "Extracted memory",
                                                  JsonUtils.schema(AgentMemoryOutput.class));
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> void consume(JsonNode output, A agent) {
        try {
            final var memoryOutput = objectMapper.treeToValue(output, AgentMemoryOutput.class);
            final var memories = Objects.requireNonNullElseGet(
                    memoryOutput.getGeneratedMemory(), List::<GeneratedMemoryUnit>of);
            memories.stream()
                    .filter(memoryUnit -> memoryUnit.getReusabilityScore() >= minRelevantReusabilityScore)
                    .forEach(memoryUnit -> {
                        log.debug("Saving memory: {} of type: {} for scope: {} and scopeId: {}. Content: {}",
                                  memoryUnit.getName(),
                                  memoryUnit.getType(),
                                  memoryUnit.getScope(),
                                  memoryUnit.getScopeId(),
                                  memoryUnit.getContent());
                        memoryStore.save(
                                AgentMemory.builder()
                                        .scope(memoryUnit.getScope())
                                        .scopeId(memoryUnit.getScopeId())
                                        .agentName(agent.name())
                                        .memoryType(memoryUnit.getType())
                                        .name(memoryUnit.getName())
                                        .content(memoryUnit.getContent())
                                        .topics(memoryUnit.getTopics())
                                        .reusabilityScore(memoryUnit.getReusabilityScore())
                                        .build());
                    });
        }
        catch (Exception e) {
            log.error("Error converting json node to memory output. Error: %s Json: %s"
                              .formatted(AgentUtils.rootCause(e).getMessage(), output), e);
        }

    }

    @Override
    public <R, T, A extends Agent<R, T, A>> void onRegistrationCompleted(A agent) {
        agent.onRequestCompleted()
                .connect(this::extractMemory);
    }

    @SneakyThrows
    @SuppressWarnings("unchecked")
    private <T, A extends Agent<R, T, A>, R> void extractMemory(Agent.ProcessingCompletedData<R, T, A> data) {
        if(!saveMemoryAfterSessionEnd) {
            log.debug("Memory extraction is disabled");
            return;
        }
        if(!data.getProcessingMode().equals(ProcessingMode.STREAMING)) {
            log.debug("Skipping async memory extraction as the request was processed directly");
            return;
        }
        final var output = data.getAgentSetup().getModel()
                .runDirect(data.getContext(),
                           objectMapper.writeValueAsString(extractionTaskPrompt()),
                           memorySchema(),
                           data.getOutput().getAllMessages())
                .join();
        if(output.getError() != null) {
            log.error("Error extracting memory: {}", output.getError());
        }
        else {
            final var outputData = output.getData();
            if(!outputData.isEmpty()) {
                log.debug("Extracted memory output: {}", outputData);
                consume(output.getData(), (A)data.getAgent());
            }
            else {
                log.debug("No memory extracted from the output");
            }
        }
    }

    @Tool("Retrieve relevant memories based on topics and query derived from the current conversation")
    public List<Fact> findMemories(
            @JsonPropertyDescription("query to be used to search for memories") final String query) {
        log.debug("Memory query: {}", query /*topics*/);
        final var facts = memoryStore.findMemories(null,
                                                   null,
                                                   EnumSet.allOf(MemoryType.class),
                                                   List.of(),
                                                   query,
                                                   minRelevantReusabilityScore,
                                                   20)
                .stream()
                .map(agentMemory -> new Fact(agentMemory.getName(), agentMemory.getContent()))
                .toList();
        if (log.isDebugEnabled()) {
            log.debug("Retrieved memories: {}", facts);
        }
        return facts;
    }

    @Override
    public Map<String, ExecutableTool> tools() {
        return this.tools;
    }
}
