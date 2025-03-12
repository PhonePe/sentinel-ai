package com.phonepe.sentinelai.core.agentmemory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRequestMetadata;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An extension for memory management
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
    public <R> List<String> systemPrompts(R request, AgentRequestMetadata metadata) {
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
        
        """);

        if(options.isUpdateSessionSummary()) {
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
            if(!memoriesAboutUser.isEmpty()) {
                prompts.add("Here are some memories about the user:");
                memoriesAboutUser.forEach(memory -> prompts.add(" - Memory: [%s] : %s"
                                                               .formatted(memory.getName(), memory.getContent())));
            }
        }

        return prompts;
    }

    @Override
    public JsonNode outputSchema() {
        return JsonUtils.schema(MemoryOutput.class);
    }

    @Override
    public void consume(JsonNode output) {
        try {
            final var memoryOutput = objectMapper.treeToValue(output, MemoryOutput.class);

            saveMemories(memoryOutput.getGlobalMemory(), MemoryScope.AGENT, "test");
            saveMemories(memoryOutput.getSessionMemories(), MemoryScope.SESSION, memoryOutput.getSessionId());
            saveMemories(memoryOutput.getUserMemories(), MemoryScope.ENTITY, memoryOutput.getUserId());
            log.info("Session summary: {}", memoryOutput.getSessionSummary());
        }
        catch (Exception e) {
            log.error("Error converting json node to memory output. Error: %s Json: %s".formatted(e.getMessage(),
                                                                                                  output), e);
        }

    }

    @Override
    public String outputKey() {
        return OUTPUT_KEY;
    }

    private void saveMemories(
            List<GeneratedMemoryUnit> memories,
            MemoryScope scope,
            String scopeId) {
        Objects.requireNonNullElseGet(memories, List::<GeneratedMemoryUnit>of)
                .forEach(memoryUnit -> {
                    options.getMemoryStore().createOrUpdate(AgentMemory.builder()
                                                                    .scope(scope)
                                                                    .scopeId(scopeId)
                                                                    .agentName("test")
                                                                    .memoryType(memoryUnit.getType())
                                                                    .name(memoryUnit.getName())
                                                                    .content(memoryUnit.getContent())
                                                                    .topics(memoryUnit.getTopics())
                                                                    .reusabilityScore(memoryUnit.getReusabilityScore())
                                                                    .build());
                });


    }

    @Tool("Tool used to update session summary")
    public boolean updateSessionSummary(String sessionId, String content, List<String> topics) {
        final var sessionSummary = options.getMemoryStore()
                .updateSessionSummary(sessionId, content, topics)
                .map(AgentMemory::getContent)
                .orElse(null);
        if (Strings.isNullOrEmpty(sessionSummary)) {
            log.error("Session summary is not updated for session: {}", sessionId);
            return false;
        }

        log.info("Updated session summary: {}", sessionSummary);

        return true;
    }
}
