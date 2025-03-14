package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.agent.SystemPromptSchema;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
import lombok.Data;
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

    @Data
    private static final class Fact {
        private String name;
        private String content;
    }


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
    public <R, D, T, A extends Agent<R, D, T, A>> ExtensionPromptSchema additionalSystemPrompts(
            R request,
            AgentRequestMetadata metadata,
            A agent) {
        final var prompts = new ArrayList<SystemPromptSchema.SecondaryTask>();
        //TODO::IF ID IS EXPOSED IN MEMORY, WILL WE BE ABLE TO UPDATE THEM?
        if (saveMemoryAfterSessionEnd) {
            //Add extract prompt only if extraction is needed
            final var prompt = new SystemPromptSchema.SecondaryTask()
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
                prompt.setTools(tools.values()
                        .stream()
                        .map(tool -> new SystemPromptSchema.ToolSummary()
                                .setName(tool.getToolDefinition().getName())
                                .setDescription(tool.getToolDefinition().getDescription()))
                        .toList());

            }
            prompts.add(prompt);
        }

        final var memories = new ArrayList<AgentMemory>();
//        //Add relevant existing memories to the prompt
        if (!Strings.isNullOrEmpty(metadata.getUserId())) {

            final var memoriesAboutUser = memoryStore
                    .findMemoriesAboutUser(metadata.getUserId(), null, null, 5);
            if (!memoriesAboutUser.isEmpty()) {
                memories.addAll(memoriesAboutUser);
            }
        }

        if (!Strings.isNullOrEmpty(metadata.getSessionId())) {
            final var memoriesAboutSession = memoryStore
                    .findMemories(metadata.getSessionId(), MemoryScope.ENTITY, null, "", List.of(), 5);
            if (!memoriesAboutSession.isEmpty()) {
                memories.addAll(memoriesAboutSession);
            }
        }

        return new ExtensionPromptSchema(prompts, List.of("IMPORTANT INSTRUCTION: USE MEMORY ABOUT USER, SESSION AND YOURSELF WHEREVER APPLICABLE",
                                                          memories));
    }

    @Override
    public Optional<AgentExtensionOutputDefinition> outputSchema() {
        return Optional.of(new AgentExtensionOutputDefinition(OUTPUT_KEY,
                                                              "Extracted memory",
                                                              JsonUtils.schema(MemoryOutput.class)));
    }

    @Override
    public <R, D, T, A extends Agent<R, D, T, A>> void consume(JsonNode output, A agent) {
        try {
            final var memoryOutput = objectMapper.treeToValue(output, MemoryOutput.class);

            saveMemories(memoryOutput.getGlobalMemory(), MemoryScope.AGENT, agent.name(), agent);
            saveMemories(memoryOutput.getSessionMemories(), MemoryScope.SESSION, memoryOutput.getSessionId(), agent);
            saveMemories(memoryOutput.getUserMemories(), MemoryScope.ENTITY, memoryOutput.getUserId(), agent);
        }
        catch (Exception e) {
            log.error("Error converting json node to memory output. Error: %s Json: %s".formatted(e.getMessage(),
                                                                                                  output), e);
        }

    }

    private <R, D, T, A extends Agent<R, D, T, A>> void saveMemories(
            List<GeneratedMemoryUnit> memories,
            MemoryScope scope,
            String scopeId,
            A agent) {
        Objects.requireNonNullElseGet(memories, List::<GeneratedMemoryUnit>of)
                .forEach(memoryUnit -> memoryStore.createOrUpdate(
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
