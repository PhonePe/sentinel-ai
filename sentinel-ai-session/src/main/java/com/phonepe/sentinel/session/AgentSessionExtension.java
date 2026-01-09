package com.phonepe.sentinel.session;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.phonepe.sentinel.session.history.modifiers.FailedToolCallRemovalModifier;
import com.phonepe.sentinel.session.history.modifiers.SystemPromptRemovalModifier;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.agentmessages.*;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.tools.NonContextualDefaultExternalToolRunner;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;


/**
 * Manages session for an agent
 */
@Slf4j
@Getter(value = AccessLevel.PACKAGE, onMethod_ = {@VisibleForTesting})
public class AgentSessionExtension<R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {
    private static final String OUTPUT_KEY = "sessionOutput";
    ObjectMapper mapper;
    SessionStore sessionStore;
    AgentSessionExtensionSetup setup;
    List<BiFunction<AgentRunContext<R>, List<AgentMessage>, List<AgentMessage>>> historyModifiers;

    @Builder
    public AgentSessionExtension(
            ObjectMapper mapper,
            SessionStore sessionStore,
            AgentSessionExtensionSetup setup,
            List<BiFunction<AgentRunContext<R>, List<AgentMessage>, List<AgentMessage>>> historyModifiers) {
        this.mapper = Objects.requireNonNullElseGet(mapper, JsonUtils::createMapper);
        this.setup = Objects.requireNonNullElse(setup, AgentSessionExtensionSetup.DEFAULT);
        if (isSummaryEnabled() || isHistoryEnabled()) {
            Objects.requireNonNull(sessionStore, "SessionStore cannot be null when SUMMARY mode is enabled");
        }
        this.sessionStore = sessionStore;
        this.historyModifiers = new ArrayList<>(Objects.requireNonNullElseGet(historyModifiers, List::of));
    }

    public AgentSessionExtension<R, T, A> addHistoryModifier(
            BiFunction<AgentRunContext<R>, List<AgentMessage>, List<AgentMessage>> modifier) {
        this.addHistoryModifiers(List.of(modifier));
        return this;
    }

    public AgentSessionExtension<R, T, A> addHistoryModifiers(
            List<BiFunction<AgentRunContext<R>, List<AgentMessage>, List<AgentMessage>>> modifiers) {
        this.historyModifiers.addAll(modifiers);
        return this;
    }

    public AgentSessionExtension<R, T, A> addDefaultModifiers() {
        return addHistoryModifiers(List.of(
                new SystemPromptRemovalModifier<>(),
                new FailedToolCallRemovalModifier<>()));
    }

    @Override
    public String name() {
        return "agent-session";
    }

    @Override
    public List<FactList> facts(
            R request,
            AgentRunContext<R> context,
            A agent) {
        final var sessionId = AgentUtils.sessionId(context);
        if (!isSummaryEnabled() || Strings.isNullOrEmpty(sessionId)) {
            log.trace(
                    "Session extension summary feature is disabled or session id is missing. Skipping fact generation" +
                            ".");
            return List.of();
        }
        return sessionStore.session(sessionId)
                .map(sessionSummary -> List.of(
                        new FactList("Information about session %s".formatted(sessionId),
                                     List.of(new Fact("A summary of the conversation in this session",
                                                      sessionSummary.getSummary())))))
                .orElse(List.of());
    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(
            R request,
            AgentRunContext<R> context,
            A agent,
            ProcessingMode processingMode) {
        final var prompts = new ArrayList<SystemPrompt.Task>();
        if (isSummaryEnabled() && processingMode.equals(ProcessingMode.DIRECT)) {
            prompts.add(sessionAndRunSummaryExtractionTaskPrompt(AgentUtils.sessionId(context)));
        }

        final var hints = new ArrayList<>();
        if (!Strings.isNullOrEmpty(AgentUtils.sessionId(context))) {
            hints.add("USE SESSION INFORMATION TO CONTEXTUALIZE RESPONSES");
        }
        return new ExtensionPromptSchema(prompts, hints);
    }


    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        // We summarize and store asynchronously
        return Optional.empty();
    }

    @Data
    @RequiredArgsConstructor
    private static final class ToolCallData {
        private final String messageId;
        boolean hasRequest;
        boolean hasResponse;
    }

    @Override
    public List<AgentMessage> messages(AgentRunContext<R> context, A agent, R request) {
        if (!isHistoryEnabled()) {
            return List.of();
        }
        final var sessionId = AgentUtils.sessionId(context);
        if (!Strings.isNullOrEmpty(sessionId)) {
            final var agentMessages = sessionStore.readMessages(sessionId,
                                                                setup.getMaxHistoryMessages(),
                                                                true);
            if (agentMessages.isEmpty()) {
                log.info("No messages found for session {}", sessionId);
                return List.of();
            }
            //Remove all partial tool calls
            //create a map of tool calls that are incomplete, ie has request and not response or response and not
            // request
            final var toolCallDataMap = new HashMap<String, ToolCallData>();
            for (var message : agentMessages) {
                log.debug("Fetched message for session {}: {}", sessionId, message);
                message.accept(new AgentMessageVisitor<Void>() {
                    @Override
                    public Void visit(AgentRequest request) {
                        return request.accept(new AgentRequestVisitor<Void>() {
                            ;

                            @Override
                            public Void visit(com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt systemPrompt) {
                                return null;
                            }

                            @Override
                            public Void visit(UserPrompt userPrompt) {
                                return null;
                            }

                            @Override
                            public Void visit(ToolCallResponse toolCallResponse) {
                                toolCallDataMap.computeIfAbsent(toolCallResponse.getToolCallId(),
                                                                k -> new ToolCallData(toolCallResponse.getMessageId()))
                                        .setHasResponse(true);
                                return null;
                            }
                        });
                    }

                    @Override
                    public Void visit(AgentResponse response) {
                        return response.accept(new AgentResponseVisitor<Void>() {
                            ;

                            @Override
                            public Void visit(Text text) {
                                return null;
                            }

                            @Override
                            public Void visit(StructuredOutput structuredOutput) {
                                return null;
                            }

                            @Override
                            public Void visit(ToolCall toolCall) {
                                toolCallDataMap.computeIfAbsent(toolCall.getToolCallId(),
                                                                k -> new ToolCallData(toolCall.getMessageId()))
                                        .setHasRequest(true);
                                return null;
                            }
                        });
                    }

                    @Override
                    public Void visit(AgentGenericMessage genericMessage) {
                        return null;
                    }
                });
            }

            final var nonPairedCalls = toolCallDataMap.values()
                    .stream()
                    .filter(toolCallData -> toolCallData.isHasRequest() != toolCallData.isHasResponse())
                    .map(ToolCallData::getMessageId)
                    .collect(Collectors.toUnmodifiableSet());
            log.info("Found unpaired tool call message IDs: {}", nonPairedCalls);
            final var allowedMessages = new ArrayList<>(agentMessages);
            allowedMessages.removeIf(message -> nonPairedCalls.contains(message.getMessageId()));
            log.info("Returning {} messages for session {} after removing unpaired tool calls",
                     allowedMessages.size(),
                     sessionId);
            return allowedMessages;
        }
        return List.of();
    }

    @Override
    public void onExtensionRegistrationCompleted(A agent) {
        agent.onRequestCompleted().connect(this::extractAndStoreHistory);
    }

    private SystemPrompt.Task sessionAndRunSummaryExtractionTaskPrompt(String sessionId) {
        final var prompt = SystemPrompt.Task.builder()
                .objective("UPDATE SESSION AND RUN SUMMARY")
                .outputField(OUTPUT_KEY)
                .instructions(
                        """
                                - Generate %d character summary for session %s based on all the messages.
                                - Include single word tags based on the topics being discussed.
                                """
                                .formatted(setup.getMaxSummaryLength(), sessionId));

        return prompt.build();
    }

    private static ModelOutputDefinition sessionSummarySchema() {
        return new ModelOutputDefinition(
                OUTPUT_KEY,
                "Schema summary for this session and run",
                JsonUtils.schema(Summary.class));
    }

    @SneakyThrows
    private void extractAndStoreHistory(Agent.ProcessingCompletedData<R, T, A> data) {
        saveMessages(data.getContext(), data.getOutput().getAllMessages());
        if (isSummaryEnabled()) {
            summarizeConversation(data);
        }
    }

    /**
     * Reads the last {@link AgentSessionExtensionSetup#getSummarizationThreshold()} messages and summarizes them.
     * Updates session with the generated summary.
     *
     * @param data Completed Data
     */
    private void summarizeConversation(Agent.ProcessingCompletedData<R, T, A> data) {
        try {
            summarizeConversationImpl(data);
        }
        catch (JsonProcessingException e) {
            log.error("Error during conversation summarization: {}", e.getMessage(), e);
        }
    }

    @Value
    @JsonClassDescription("Summary of the session till now and the current run")
    private static class Summary {

        @JsonPropertyDescription("A summary of the conversation thus far between the user and the agent. " +
                "Formatted in a structured manner so that it can be used by an LLM to understand the conversation " +
                "history thus far without needing all the raw messages")
        String sessionSummary;

        @JsonPropertyDescription("A short title for the session summarizing the main topic being discussed")
        String title;

        @JsonPropertyDescription("Important one-word keywords/topics being discussed in the session")
        List<String> keywords;
    }

    private void summarizeConversationImpl(Agent.ProcessingCompletedData<R, T, A> data) throws JsonProcessingException {
        final var context = data.getContext();

        final var sessionId = AgentUtils.sessionId(context);
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt(
                sessionId,
                context.getRunId(),
                mapper.writeValueAsString(sessionAndRunSummaryExtractionTaskPrompt(sessionId
                                                                                  )),
                false,
                null));
        messages.add(new UserPrompt(
                sessionId,
                context.getRunId(),
                ("Generate a %d character summary of the conversation between user and agent from the following " +
                        "messages. Messages JSON: %s")
                        .formatted(setup.getMaxSummaryLength(),
                                   mapper.writeValueAsString(sessionStore.readMessages(sessionId,
                                                                                       setup.getSummarizationThreshold(),
                                                                                       false))),
                LocalDateTime.now()));
        final var runId = "message-summarization-" + UUID.randomUUID();
        final var modelRunContext = new ModelRunContext(data.getAgent().name(),
                                                        runId,
                                                        sessionId,
                                                        AgentUtils.userId(context),
                                                        data.getAgentSetup()
                                                                .withModelSettings(data.getAgentSetup()
                                                                                           .getModelSettings()
                                                                                           .withParallelToolCalls(false)),
                                                        context.getModelUsageStats(),
                                                        ProcessingMode.DIRECT);
        final var output = data.getAgentSetup()
                .getModel()
                .compute(modelRunContext,
                         List.of(sessionSummarySchema()),
                         messages,
                         Map.of(),
                         new NonContextualDefaultExternalToolRunner(sessionId, runId, mapper),
                         new NeverTerminateEarlyStrategy(),
                         List.of())
                .join();
        if (output.getError() != null && !output.getError().getErrorType().equals(ErrorType.SUCCESS)) {
            log.error("Error extracting memory: {}", output.getError());
        }
        else {
            final var summaryData = output.getData().get(OUTPUT_KEY);
            if (JsonUtils.empty(summaryData)) {
                log.debug("No summary extracted from the output");
            }
            else {
                log.debug("Extracted session summary output: {}", summaryData);
                saveSummary(context, summaryData, data.getAgent());
            }
        }
    }

    private void saveSummary(AgentRunContext<R> context, JsonNode output, A agent) {
        if (isSummaryEnabled()) {
            final var sessionId = AgentUtils.sessionId(context);
            try {
                final var summary = mapper.treeToValue(output, Summary.class);
                final var updated = sessionStore.saveSession(agent.name(),
                                                             new SessionSummary(sessionId,
                                                                                summary.getTitle(),
                                                                                summary.getSessionSummary(),
                                                                                summary.getKeywords(),
                                                                                AgentUtils.epochMicro()))
                        .orElse(null);
                log.info("Session summary: {}", updated);
            }
            catch (JsonProcessingException e) {
                log.error("Error converting json node to memory output. Error: %s Json: %s"
                                  .formatted(e.getMessage(), output), e);
            }
        }
    }

    private void saveMessages(AgentRunContext<R> context, List<AgentMessage> messages) {
        if (!isHistoryEnabled()) {
            return;
        }
        final var sessionId = AgentUtils.sessionId(context);
        if (Strings.isNullOrEmpty(sessionId)) {
            log.warn("No session id found in context. History storage will be skipped");
            return;
        }

        var newMessages = messages.stream()
                .filter(message -> message.getRunId().equals(context.getRunId()))
                .toList();

        for (var modifier : historyModifiers) {
            newMessages = modifier.apply(context, newMessages);
        }
        if (newMessages.isEmpty()) {
            log.warn("No new messages to save after applying modifiers");
            return;
        }
        sessionStore.saveMessages(sessionId, context.getRunId(), newMessages);
        log.info("Saved {} messages to session {}", newMessages.size(), sessionId);
        if (log.isDebugEnabled()) {
            log.debug("Saved message IDs: {}", newMessages.stream().map(AgentMessage::getMessageId).toList());
        }
    }

    private boolean isSummaryEnabled() {
        return this.setup.getFeatures().contains(AgentSessionExtensionFeature.SUMMARY);
    }

    private boolean isHistoryEnabled() {
        return this.setup.getFeatures().contains(AgentSessionExtensionFeature.HISTORY);
    }
}
