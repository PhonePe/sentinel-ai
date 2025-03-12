package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
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
@Builder
public class MemoryExtension implements AgentExtension {
    private static final String OUTPUT_KEY = "memoryOutput";

    private final AgentMemoryOptions options;
    private final ObjectMapper objectMapper;

    public MemoryExtension(AgentMemoryOptions options, ObjectMapper objectMapper) {
        this.options = options;
        this.objectMapper = objectMapper;
    }


    @Override
    public String name() {
        return "agent-memory-extension";
    }

    @Override
    public <R, D, T, A extends Agent<R, D, T, A>> List<String> additionalSystemPrompts(
            R request,
            AgentRequestMetadata metadata,
            A agent) {
        if (options == null || !options.isSaveMemoryAfterSessionEnd()) {
            log.info("Memory is not enabled");
            return List.of();
        }
        final var prompts = new ArrayList<String>();
        //TODO::IF ID IS EXPOSED IN MEMORY, WILL WE BE ABLE TO UPDATE THEM?
        prompts.add("""
                    ## MEMORY EXTRACTION
                    Please extract different memories from messages and populate the field `memoryOutput` with the extracted memories
                    
                    Use the following rules for memory extraction:
                    - SEMANTIC: Extract fact about the session or user or any other subject
                    - EPISODIC: Extract a specific event or episode from the conversation
                    - PROCEDURAL: Extract a procedure as a list of steps or a sequence of actions that you can use later
                    
                    IMPORTANT INSTRUCTION FOR MEMORY EXTRACTION:
                    - Do not include non-reusable information as memories.
                    - Extract as many useful memories as possible
                    """);

        if (options.isUpdateSessionSummary()) {
            prompts.add("""
                        ## UPDATE SESSION SUMMARY
                         Generate session summary and a list of topics being discussed in the session based on the last few messages.
                        """);
        }
        if (!Strings.isNullOrEmpty(metadata.getSessionId())) {
            prompts.add("#### FACTS ABOUT THE CURRENT CONVERSATION SESSION:");
            prompts.add(" - Current session ID is: " + metadata.getSessionId());
            options.getMemoryStore().sessionSummary(metadata.getSessionId())
                    .ifPresent(sessionMemory -> {
                        prompts.add(" - Summary of discussions in current session: " + sessionMemory.getContent());
                        prompts.add(" - Topics being discussed in session: "
                                            + Joiner.on(",").join(sessionMemory.getTopics()));
                    });
        }
        if (!Strings.isNullOrEmpty(metadata.getUserId())) {
            prompts.add("### FACTS ABOUT THE USER");
            prompts.add(" - User's user ID is: " + metadata.getUserId());
            final var memoriesAboutUser = options.getMemoryStore()
                    .findMemoriesAboutUser(metadata.getUserId(), null, null, 5);
            if (!memoriesAboutUser.isEmpty()) {
                prompts.add("Here are some memories about the user:");
                memoriesAboutUser.forEach(memory -> prompts.add(" - Memory: [%s] : %s"
                                                                        .formatted(memory.getName(),
                                                                                   memory.getContent())));
            }
        }

        return prompts;
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
            log.info("Session summary: {}", memoryOutput.getSessionSummary());
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
                .forEach(memoryUnit -> options.getMemoryStore().createOrUpdate(
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
