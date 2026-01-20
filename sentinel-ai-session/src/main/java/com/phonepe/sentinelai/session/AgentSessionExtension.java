package com.phonepe.sentinelai.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.session.history.modifiers.FailedToolCallRemovalPreFilter;
import com.phonepe.sentinelai.session.history.modifiers.MessagePersistencePreFilter;
import com.phonepe.sentinelai.session.history.modifiers.SystemPromptRemovalPreFilter;
import com.phonepe.sentinelai.session.history.selectors.MessageSelector;
import com.phonepe.sentinelai.session.history.selectors.UnpairedToolCallsRemover;
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
import com.phonepe.sentinelai.core.tools.NonContextualDefaultExternalToolRunner;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;


/**
 * Manages session for an agent. Saves the message history and summarizes the session after each run.
 * Injects session summary as fact in the system prompt. Also provides messages from the session history to the agent.
 */
@Slf4j
@Getter(value = AccessLevel.PACKAGE, onMethod_ = {@VisibleForTesting})
public class AgentSessionExtension<R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {
    private static final String OUTPUT_KEY = "sessionOutput";

    ObjectMapper mapper;
    SessionStore sessionStore;
    AgentSessionExtensionSetup setup;
    List<MessagePersistencePreFilter<R>> historyModifiers;
    List<MessageSelector> messageSelectors;

    @Builder
    public AgentSessionExtension(
            ObjectMapper mapper,
            @NonNull SessionStore sessionStore,
            AgentSessionExtensionSetup setup,
            List<MessagePersistencePreFilter<R>> historyModifiers,
            List<MessageSelector> messageSelectors) {
        this.mapper = Objects.requireNonNullElseGet(mapper, JsonUtils::createMapper);
        this.setup = Objects.requireNonNullElse(setup, AgentSessionExtensionSetup.DEFAULT);
        this.sessionStore = sessionStore;
        this.historyModifiers = new CopyOnWriteArrayList<>(
                Objects.requireNonNullElseGet(
                        historyModifiers,
                        () -> List.of(new SystemPromptRemovalPreFilter<>(),
                                      new FailedToolCallRemovalPreFilter<>())));
        this.messageSelectors = new CopyOnWriteArrayList<>(
                Objects.requireNonNullElseGet(
                        messageSelectors,
                        () -> List.of(new UnpairedToolCallsRemover())));
    }

    public AgentSessionExtension<R, T, A> addMessagePersistencePreFilter(
            MessagePersistencePreFilter<R> modifier) {
        return addMessagePersistencePreFilters(List.of(modifier));
    }

    public AgentSessionExtension<R, T, A> addMessagePersistencePreFilters(
            List<MessagePersistencePreFilter<R>> modifiers) {
        this.historyModifiers.addAll(modifiers);
        return this;
    }

    public AgentSessionExtension<R, T, A> resetMessagePersistencePreFilters() {
        this.historyModifiers.clear();
        log.warn("All message persistence pre-filters have been cleared.");
        return this;
    }

    public AgentSessionExtension<R, T, A> addMessageSelector(MessageSelector selector) {
        return addMessageSelectors(List.of(selector));
    }

    public AgentSessionExtension<R, T, A> addMessageSelectors(List<MessageSelector> selectors) {
        this.messageSelectors.addAll(selectors);
        return this;
    }

    public AgentSessionExtension<R, T, A> resetMessageSelectors() {
        this.messageSelectors.clear();
        log.warn("All message selectors have been cleared.");
        return this;
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
        final var hints = new ArrayList<>();
        if (!Strings.isNullOrEmpty(AgentUtils.sessionId(context))) {
            hints.add("USE SESSION INFORMATION TO CONTEXTUALIZE RESPONSES");
        }
        return new ExtensionPromptSchema(List.of(), hints);
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        // We summarize and store asynchronously
        return Optional.empty();
    }


    @Override
    public List<AgentMessage> messages(AgentRunContext<R> context, A agent, R request) {
        final var sessionId = AgentUtils.sessionId(context);
        if (!Strings.isNullOrEmpty(sessionId)) {
            final var messagesToFetch = Math.max(
                    setup.getHistoricalMessagesCount(),
                    setup.getHistoricalMessageFetchSize());
            final var agentMessages = readMessages(sessionId, messagesToFetch, true)
                    .getItems();
            if (agentMessages.isEmpty()) {
                log.info("No messages found for session {}", sessionId);
                return List.of();
            }
            return new UnpairedToolCallsRemover().select(sessionId, agentMessages);
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
                JsonUtils.schema(ExtractedSummary.class));
    }

    @SneakyThrows
    private void extractAndStoreHistory(Agent.ProcessingCompletedData<R, T, A> data) {
        saveMessages(data.getContext(), data.getOutput().getAllMessages());
        if (isSummaryEnabled()) {
            summarizeConversation(data);
        }
    }

    /**
     * Reads the last {@link AgentSessionExtensionSetup#getMaxMessagesToSummarize()} messages and summarizes them.
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
                                   mapper.writeValueAsString(readMessages(sessionId,
                                                                          setup.getMaxMessagesToSummarize(),
                                                                          false
                                                                         ))),
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

    private SessionStore.ListResponse<AgentMessage> readMessages(
            String sessionId,
            int count,
            boolean skipSystemPrompt) {
        int totalMessagesCount = 0;
        var outputMessages = new ArrayList<AgentMessage>();
        var pointer = "";
        do {
            final var response = sessionStore.readMessages(sessionId, count, skipSystemPrompt, pointer);
            var messages = response.getItems();
            pointer = response.getNextPageToken();
            if (messages.isEmpty()) {
                break;
            }
            for (final var filter : messageSelectors) {
                messages = filter.select(sessionId, messages);
            }
            outputMessages.addAll(messages);
            totalMessagesCount += messages.size();
        } while (totalMessagesCount < count && pointer != null);
        final var nextPageToken = totalMessagesCount >= count ? pointer : null;
        return new SessionStore.ListResponse<>(outputMessages.stream().limit(count).toList(), nextPageToken);
    }

    private void saveSummary(AgentRunContext<R> context, JsonNode output, A agent) {
        if (isSummaryEnabled()) {
            final var sessionId = AgentUtils.sessionId(context);
            try {
                final var summary = mapper.treeToValue(output, ExtractedSummary.class);
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
        final var sessionId = AgentUtils.sessionId(context);
        if (Strings.isNullOrEmpty(sessionId)) {
            log.warn("No session id found in context. History storage will be skipped");
            return;
        }

        var newMessages = messages.stream()
                .filter(message -> message.getRunId().equals(context.getRunId()))
                .toList();

        for (var modifier : historyModifiers) {
            newMessages = modifier.filter(context, newMessages);
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
        return !this.setup.isDisableSummarization();
    }

}
