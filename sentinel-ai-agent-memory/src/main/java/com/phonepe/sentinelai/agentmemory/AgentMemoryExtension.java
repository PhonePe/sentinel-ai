/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.Fact;
import com.phonepe.sentinelai.core.agent.FactList;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agent.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.NonContextualDefaultExternalToolRunner;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.core.utils.ToolUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An extension for memory management.
 * We do this in a straight forward manner.  If anything is available, inject it into system prompt.
 * If output has memory store it. No tools are needed.
 */
@Slf4j
public class AgentMemoryExtension<R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {
    private static final String OUTPUT_KEY = "memoryOutput";

    /**
     * Whether to save memory after session ends.
     * If true, the extension will extract memories from the session and save them in the memory store.
     */
    private final MemoryExtractionMode memoryExtractionMode;
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

    private A agent;

    @Builder
    public AgentMemoryExtension(
            MemoryExtractionMode memoryExtractionMode,
            @NonNull AgentMemoryStore memoryStore,
            ObjectMapper objectMapper,
            int minRelevantReusabilityScore) {
        this.memoryExtractionMode = Objects.requireNonNullElse(memoryExtractionMode, MemoryExtractionMode.INLINE);
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
    public List<FactList> facts(
            R request,
            AgentRunContext<R> context,
            A agent) {
        final var memories = new ArrayList<FactList>();

        //Add relevant existing memories to the prompt
        final var userId = AgentUtils.userId(context);
        if (!Strings.isNullOrEmpty(userId)) {

            final var memoriesAboutUser = memoryStore
                    .findMemoriesAboutUser(userId, null, 5);
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
    public ExtensionPromptSchema additionalSystemPrompts(
            R request,
            AgentRunContext<R> context,
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
                                                  .name(tool.getToolDefinition().getId())
                                                  .description(tool.getToolDefinition().getDescription())
                                                  .build())
                                          .toList())
                            .build());
        //Structured output is not supported in streaming mode so for streaming mode extraction is always out of band
        //For direct mode, extraction can be inline or out of band or disabled altogether based on the memory
        // extraction mode
        if (memoryExtractionMode.equals(MemoryExtractionMode.INLINE) && processingMode.equals(ProcessingMode.DIRECT)) {
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
                                      
                                      scopeId will be set to agent name for AGENT scope and userId or relevant entity id for ENTITY scope. Check additional data for ids. 
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
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        if (memoryExtractionMode.equals(MemoryExtractionMode.INLINE) && processingMode.equals(ProcessingMode.DIRECT)) {
            return Optional.of(memorySchema());
        }
        log.debug("Skipping output schema for streaming mode");
        return Optional.empty();
    }

    private static ModelOutputDefinition memorySchema() {
        return new ModelOutputDefinition(OUTPUT_KEY,
                                         "Extracted memory",
                                         JsonUtils.schema(AgentMemoryOutput.class));
    }

    @Override
    public void consume(JsonNode output, A agent) {
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
    public void onExtensionRegistrationCompleted(A agent) {
        agent.onRequestCompleted()
                .connect(this::extractMemory);
    }

    @SneakyThrows
    private void extractMemory(Agent.ProcessingCompletedData<R, T, A> data) {
        if (memoryExtractionMode.equals(MemoryExtractionMode.DISABLED)) {
            log.debug("Memory extraction is disabled");
            return;
        }
        if (memoryExtractionMode.equals(MemoryExtractionMode.INLINE)) {
            if (data.getProcessingMode().equals(ProcessingMode.DIRECT)) {
                log.debug(
                        "Inline memory extraction is enabled, will extract memory from output. Out of band extraction" +
                                " is not needed.");
                return;
            }
            else {
                log.debug(
                        "Inline memory extraction is enabled, but the request was processed in streaming mode, out of" +
                                " band extraction being forced.");
            }
        }
        else {
            log.debug("Out of band memory extraction is enabled, will extract memory asynchronously");
        }
        // Replace the system prompt with the extraction task prompt
        final var messages = new ArrayList<AgentMessage>();
        //Add system prompt to the messages
        final var context = data.getContext();
        final var sessionId = AgentUtils.sessionId(context);
        messages.add(new com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt(
                sessionId,
                context.getRunId(),
                objectMapper.writeValueAsString(extractionTaskPrompt()),
                false,
                null));
        messages.add(new UserPrompt(
                sessionId,
                context.getRunId(),
                "You must extract memory from the following conversation between user and agent :" +
                        " " + objectMapper.writeValueAsString(
                        Map.of("conversation", objectMapper.writeValueAsString(data.getOutput().getNewMessages())
                              )),
                LocalDateTime.now()));
        final var runId = "mem-extraction-" + UUID.randomUUID();
        final var modelRunContext = new ModelRunContext(agent.name(),
                                                        runId,
                                                        sessionId,
                                                        AgentUtils.userId(context),
                                                        data.getAgentSetup(),
                                                        context.getModelUsageStats(),
                                                        ProcessingMode.DIRECT);
        final var output = data.getAgentSetup()
                .getModel()
                .compute(modelRunContext,
                         List.of(memorySchema()),
                         messages,
                         Map.of(),
                         new NonContextualDefaultExternalToolRunner(sessionId,
                                                                    runId,
                                                                    objectMapper),
                         new NeverTerminateEarlyStrategy(),
                         List.of())
                .join();
        if (output.getError() != null && !output.getError().getErrorType().equals(ErrorType.SUCCESS)) {
            log.error("Error extracting memory: {}", output.getError());
        }
        else {
            final var extractedMemoryData = output.getData().get(OUTPUT_KEY);
            if (JsonUtils.empty(extractedMemoryData)) {
                log.debug("No memory extracted from the output");
            }
            else {
                log.debug("Extracted memory output: {}", extractedMemoryData);
                consumeAsync(messages, extractedMemoryData, data.getAgent());
            }
        }
        log.info("Model usage stats for memory extraction run: {}", output.getUsage());
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

    @Override
    @SuppressWarnings("unchecked")
    public <M, N, O extends Agent<M, N, O>> void onToolBoxRegistrationCompleted(O agent) {
        this.agent = (A) agent;
    }
}
