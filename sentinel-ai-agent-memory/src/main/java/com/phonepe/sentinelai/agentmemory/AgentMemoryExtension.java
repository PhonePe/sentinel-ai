package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An extension for memory management.
 * We do this in a straight forward manner.  If anything is available, inject it into system prompt.
 * If output has memory store it. No tools are needed.
 */
@Slf4j
@Value
@Builder
public class AgentMemoryExtension implements AgentExtension {
    private static final String OUTPUT_KEY = "memoryOutput";




    boolean saveMemoryAfterSessionEnd;
    int numMessagesForSummarization;
    AgentMemoryStore memoryStore;
    ObjectMapper objectMapper;

    public AgentMemoryExtension(
            boolean saveMemoryAfterSessionEnd,
            int numMessagesForSummarization,
            @NonNull AgentMemoryStore memoryStore,
            ObjectMapper objectMapper) {
        this.saveMemoryAfterSessionEnd = saveMemoryAfterSessionEnd;
        this.numMessagesForSummarization = numMessagesForSummarization;
        this.memoryStore = memoryStore;
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, JsonUtils::createMapper);
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
        if (!Strings.isNullOrEmpty(metadata.getSessionId())) {
            final var memoriesAboutSession = memoryStore
                    .findMemories(metadata.getSessionId(), MemoryScope.SESSION, null, "", 5);
            if (!memoriesAboutSession.isEmpty()) {
                final var factList = new FactList("Memories about current session", memoriesAboutSession.stream()
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
            A agent) {
        final var prompts = new ArrayList<SystemPrompt.SecondaryTask>();




//        if (!memories.isEmpty()) {
//            final var memUsagePrompt = new SystemPrompt.SecondaryTask()
//                    .setObjective("USE MEMORY ABOUT USER, SESSION AND YOURSELF WHEREVER APPLICABLE")
//                    .setInstructions("Use facts provided in the facts section below to avoid making repeated tool calls for information already available ")
//                    .setAdditionalInstructions(memories);
//            prompts.add(memUsagePrompt);
//        }

        //TODO::IF ID IS EXPOSED IN MEMORY, WILL WE BE ABLE TO UPDATE THEM?
        if (saveMemoryAfterSessionEnd) {
            //Add extract prompt only if extraction is needed
            final var prompt = new SystemPrompt.SecondaryTask()
                    .setObjective("EXTRACT MEMORY FROM MESSAGES AND POPULATE `memoryOutput` FIELD")
                    .setOutputField(OUTPUT_KEY)
                    .setInstructions("""                           
                               How to extract different memory types:
                               - SEMANTIC: Extract fact about the session or user or any other subject
                               - EPISODIC: Extract a specific event or episode from the conversation
                               - PROCEDURAL: Extract a procedure as a list of steps or a sequence of actions that you can use later
                               """)
                    .setAdditionalInstructions("""
                                IMPORTANT INSTRUCTION FOR MEMORY EXTRACTION:
                                - Do not include non-reusable information as memories.
                                - Extract as many useful memories as possible
                                """);
            final var tools = this.tools();
            if(!tools.isEmpty()) {
                prompt.setTool(tools.values()
                        .stream()
                        .map(tool -> SystemPrompt.ToolSummary.builder()
                                .name(tool.getToolDefinition().getName())
                                .description(tool.getToolDefinition().getDescription())
                                .build())
                        .toList());

            }
            prompts.add(prompt);
        }




        return new ExtensionPromptSchema(prompts, List.of());
    }

    @Override
    public Optional<AgentExtensionOutputDefinition> outputSchema() {
        return Optional.of(new AgentExtensionOutputDefinition(OUTPUT_KEY,
                                                              "Extracted memory",
                                                              JsonUtils.schema(MemoryOutput.class)));
    }

    @Override
    public <R, T, A extends Agent<R, T, A>> void consume(JsonNode output, A agent) {
        try {
            final var memoryOutput = objectMapper.treeToValue(output, MemoryOutput.class);
            log.debug("Memories extracted: {}", memoryOutput);
            saveMemories(memoryOutput.getGlobalMemory(), MemoryScope.AGENT, agent.name(), agent);
            saveMemories(memoryOutput.getSessionMemories(), MemoryScope.SESSION, memoryOutput.getSessionId(), agent);
            saveMemories(memoryOutput.getUserMemories(), MemoryScope.ENTITY, memoryOutput.getUserId(), agent);
        }
        catch (Exception e) {
            log.error("Error converting json node to memory output. Error: %s Json: %s".formatted(e.getMessage(),
                                                                                                  output), e);
        }

    }

    @Tool("Find procedural memory about any topic from the store")
    public List<Fact> findProceduralMemory(@JsonPropertyDescription("keywords to find relevant procedural memory") final String query) {
        return memoryStore.findProcessMemory(query)
                .stream()
                .map(agentMemory -> new Fact(agentMemory.getName(), agentMemory.getContent()))
                .toList();
    }

    private <R, T, A extends Agent<R, T, A>> void saveMemories(
            List<GeneratedMemoryUnit> memories,
            MemoryScope scope,
            String scopeId,
            A agent) {
        Objects.requireNonNullElseGet(memories, List::<GeneratedMemoryUnit>of)
                .forEach(memoryUnit -> memoryStore.save(
                        AgentMemory.builder()
                                .scope(scope)
                                .scopeId(scopeId)
                                .agentName(agent.name())
                                .memoryType(memoryUnit.getType())
                                .name(memoryUnit.getName())
                                .content(memoryUnit.getContent())
                                .topics(memoryUnit.getTopics())
                                .reusabilityScore(memoryUnit.getReusabilityScore())
                                .build()));
    }
}
