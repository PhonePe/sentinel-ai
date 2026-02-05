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

package com.phonepe.sentinelai.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.Fact;
import com.phonepe.sentinelai.core.agent.FactList;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agent.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.compaction.ExtractedSummary;
import com.phonepe.sentinelai.core.compaction.MessageCompactor;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.session.history.modifiers.FailedToolCallRemovalPreFilter;
import com.phonepe.sentinelai.session.history.modifiers.MessagePersistencePreFilter;
import com.phonepe.sentinelai.session.history.modifiers.SystemPromptRemovalPreFilter;
import com.phonepe.sentinelai.session.history.selectors.MessageSelector;
import com.phonepe.sentinelai.session.history.selectors.UnpairedToolCallsRemover;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import static com.phonepe.sentinelai.session.MessageReadingUtils.readMessagesSinceId;
import static com.phonepe.sentinelai.session.MessageReadingUtils.rearrangeMessages;


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

    /**
     * System prompt to be used for summarization.
     * Clients may override to customize the prompt.
     *
     * @return true if enabled, false otherwise
     */
    protected String name;

    @Builder
    public AgentSessionExtension(ObjectMapper mapper,
                                 @NonNull SessionStore sessionStore,
                                 AgentSessionExtensionSetup setup,
                                 List<MessagePersistencePreFilter<R>> historyModifiers,
                                 List<MessageSelector> messageSelectors) {
        this.mapper = Objects.requireNonNullElseGet(mapper,
                                                    JsonUtils::createMapper);
        this.setup = Objects.requireNonNullElse(setup,
                                                AgentSessionExtensionSetup.DEFAULT);
        this.sessionStore = sessionStore;
        this.historyModifiers = new CopyOnWriteArrayList<>(Objects
                .requireNonNullElseGet(historyModifiers,
                                       () -> List.of(
                                                     new SystemPromptRemovalPreFilter<>(),
                                                     new FailedToolCallRemovalPreFilter<>())));
        this.messageSelectors = new CopyOnWriteArrayList<>(Objects
                .requireNonNullElseGet(messageSelectors,
                                       () -> List.of(
                                                     new UnpairedToolCallsRemover())));
    }

    public AgentSessionExtension<R, T, A> addMessagePersistencePreFilter(MessagePersistencePreFilter<R> modifier) {
        return addMessagePersistencePreFilters(List.of(modifier));
    }

    public AgentSessionExtension<R, T, A> addMessagePersistencePreFilters(List<MessagePersistencePreFilter<R>> modifiers) {
        this.historyModifiers.addAll(modifiers);
        return this;
    }

    public AgentSessionExtension<R, T, A> addMessageSelector(MessageSelector selector) {
        return addMessageSelectors(List.of(selector));
    }

    public AgentSessionExtension<R, T, A> addMessageSelectors(List<MessageSelector> selectors) {
        this.messageSelectors.addAll(selectors);
        return this;
    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(R request,
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
    public List<FactList> facts(R request,
                                AgentRunContext<R> context,
                                A agent) {
        final var sessionId = AgentUtils.sessionId(context);
        if (Strings.isNullOrEmpty(sessionId)) {
            log.trace("No session id found in context. No session facts will be added.");
            return List.of();
        }
        return sessionStore.session(sessionId)
                .map(sessionSummary -> List.of(new FactList(
                                                            "Information about session %s"
                                                                    .formatted(sessionId),
                                                            List.of(new Fact("A summary of the conversation in this session",
                                                                             sessionSummary
                                                                                     .getSummary())))))
                .orElse(List.of());
    }

    @Override
    public List<AgentMessage> messages(AgentRunContext<R> context,
                                       A agent,
                                       R request) {
        final var sessionId = AgentUtils.sessionId(context);
        if (Strings.isNullOrEmpty(sessionId)) {
            log.warn("No session id found in context. No session messages will be provided.");
            return List.of();
        }
        // Find last saved session summary and extract the last message id
        final var lastSummarizedMessageId = sessionStore.session(sessionId)
                .map(SessionSummary::getLastSummarizedMessageId)
                .orElse(null);

        log.debug("Reading messages for session {} since last summarized message id {}",
                  sessionId,
                  lastSummarizedMessageId);
        final var agentMessages = readMessagesSinceId(sessionStore,
                                                      setup,
                                                      sessionId,
                                                      lastSummarizedMessageId,
                                                      true,
                                                      messageSelectors);
        if (agentMessages.isEmpty()) {
            log.info("No messages found for session {}", sessionId);
            return List.of();
        }
        return rearrangeMessages(new UnpairedToolCallsRemover().select(
                                                                       sessionId,
                                                                       agentMessages));
    }

    @Override
    public String name() {
        return "agent-session";
    }


    @Override
    public void onExtensionRegistrationCompleted(A agent) {
        agent.onRequestCompleted().connect(this::extractAndStoreHistory);
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        // We summarize and store asynchronously
        return Optional.empty();
    }

    public AgentSessionExtension<R, T, A> resetMessagePersistencePreFilters() {
        this.historyModifiers.clear();
        log.warn("All message persistence pre-filters have been cleared.");
        return this;
    }

    /**
     * Builds user prompt for summarization.
     * Clients may override to customize the prompt.
     *
     * @param context         Agent run context
     * @param sessionId       Session Id
     * @param currentSummary  Current summary
     * @param sessionMessages Messages to be summarized
     * @return UserPrompt for summarization
     * @throws JsonProcessingException Json processing exception
     */
    protected UserPrompt buildSummarizationUserPrompt(final AgentRunContext<R> context,
                                                      final String sessionId,
                                                      final String currentSummary,
                                                      final List<AgentMessage> sessionMessages) throws JsonProcessingException {
        return new UserPrompt(sessionId,
                              context.getRunId(),
                              """
                                      Generate a %d character summary of the conversation between user and \
                                      agent from the following messages.
                                      - Summary till now: %s,
                                      - Messages JSON: %s"""
                                      .formatted(setup.getMaxSummaryLength(),
                                                 Objects.requireNonNullElse(currentSummary,
                                                                            "Does not exist as this is the first compaction"),
                                                 mapper.writeValueAsString(sessionMessages)),
                              LocalDateTime.now());
    }

    SystemPrompt.Task buildSummarizationSystemPrompt(String sessionId) {
        final var prompt = SystemPrompt.Task.builder()
                .objective("UPDATE SESSION AND RUN SUMMARY")
                .outputField(OUTPUT_KEY)
                .instructions("""
                        - Generate %d character summary for session %s based on all the messages.
                        - Include at most 5 single word tags based on the topics being discussed.
                        """
                        .formatted(setup.getMaxSummaryLength(), sessionId));

        return prompt.build();
    }

    @SneakyThrows
    private void extractAndStoreHistory(Agent.ProcessingCompletedData<R, T, A> data) {
        saveMessages(data.getContext(), data.getOutput().getAllMessages());
        summarizeConversation(data);
    }

    private boolean isCompationNeeded(final Agent.ProcessingCompletedData<R, T, A> data,
                                      final List<AgentMessage> messages,
                                      final AgentSetup agentSetup) {
        if (data.getOutput()
                .getError()
                .getErrorType()
                .equals(ErrorType.LENGTH_EXCEEDED)) {
            log.warn("Compaction needed as the last run ended with LENGTH_EXCEEDED error.");
            return true;
        }
        final var estimateTokenCount = data.getAgentSetup()
                .getModel()
                .estimateTokenCount(messages, data.getAgentSetup());
        final var contextWindowSize = agentSetup.getModelSettings()
                .getModelAttributes()
                .getContextWindowSize();
        final var threshold = setup.getAutoSummarizationThresholdPercentage();
        if (threshold == 0) {
            log.debug("Compaction needed as threshold is set to 0 (Every Run).");
            return true;
        }
        final var currentBoundary = (contextWindowSize * threshold) / 100;
        final var evalResult = estimateTokenCount >= currentBoundary;
        log.debug("Automatic summarization evaluation: estimatedTokenCount={}, contextWindowSize={}, " + "threshold={}%, currentBoundary={}, needsSummarization={}",
                  estimateTokenCount,
                  contextWindowSize,
                  threshold,
                  currentBoundary,
                  evalResult);
        return evalResult;
    }

    private void saveMessages(AgentRunContext<R> context,
                              List<AgentMessage> messages) {
        final var sessionId = AgentUtils.sessionId(context);
        if (Strings.isNullOrEmpty(sessionId)) {
            log.warn("No session id found in context. History storage will be skipped");
            return;
        }

        var newMessages = messages.stream()
                .filter(message -> message.getRunId()
                        .equals(context.getRunId()))
                .toList();

        for (var modifier : historyModifiers) {
            newMessages = modifier.filter(context, newMessages);
        }
        if (newMessages.isEmpty()) {
            log.warn("No new messages to save after applying modifiers");
            return;
        }
        sessionStore.saveMessages(sessionId, context.getRunId(), newMessages);
        log.info("Saved {} messages to session {}",
                 newMessages.size(),
                 sessionId);
        if (log.isDebugEnabled()) {
            log.debug("Saved message IDs: {}",
                      newMessages.stream()
                              .map(AgentMessage::getMessageId)
                              .toList());
        }
    }

    private void saveSummary(final String sessionId,
                             final AgentRunContext<R> context,
                             final ExtractedSummary summary,
                             final String newestMessageId,
                             final String lastSummarizedMessageId) {
        try {
            final var existingSessionMessageId = sessionStore.session(sessionId)
                    .map(SessionSummary::getLastSummarizedMessageId)
                    .orElse(null);
            if (lastSummarizedMessageId != null && !Objects.equals(
                                                                   lastSummarizedMessageId,
                                                                   existingSessionMessageId)) {
                log.warn("Skipping summary save as the newest message id {} does not match existing session's last summarized message id {} for session: {}",
                         newestMessageId,
                         existingSessionMessageId,
                         sessionId);
                return;
            }
            final var updated = sessionStore.saveSession(new SessionSummary(
                                                                            sessionId,
                                                                            summary.getTitle(),
                                                                            summary.getSessionSummary(),
                                                                            summary.getKeywords(),
                                                                            newestMessageId,
                                                                            AgentUtils
                                                                                    .epochMicro()))
                    .orElse(null);
            log.info("Session summary: {}", updated);
        }
        catch (Exception e) {
            log.error("Error converting json node to memory output. Error: %s Summary: %s"
                    .formatted(e.getMessage(), summary), e);
        }
    }

    /**
     * Reads the last messages from summarization and summarizes them.
     * Updates session with the generated summary.
     *
     * @param data Completed Data
     */
    private void summarizeConversation(Agent.ProcessingCompletedData<R, T, A> data) {
        try {
            summarizeConversationImpl(data);
        }
        catch (JsonProcessingException e) {
            log.error("Error during conversation summarization: {}",
                      e.getMessage(),
                      e);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Summarization process was interrupted for session: {}",
                      AgentUtils.sessionId(data.getContext()));
        }
        catch (ExecutionException e) {
            final var error = AgentUtils.rootCause(e);
            log.error("Execution error during conversation summarization for session {}: {}",
                      AgentUtils.sessionId(data.getContext()),
                      error.getMessage(),
                      error);
        }
    }

    private void summarizeConversationImpl(Agent.ProcessingCompletedData<R, T, A> data) throws JsonProcessingException, InterruptedException, ExecutionException {
        final var context = data.getContext();

        final var sessionId = AgentUtils.sessionId(context);
        final var existingSession = sessionStore.session(sessionId)
                .orElse(null);
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt(sessionId,
                                                                                         context.getRunId(),
                                                                                         mapper.writeValueAsString(buildSummarizationSystemPrompt(sessionId)),
                                                                                         false,
                                                                                         null));
        final var lastSummarizedMessageId = AgentUtils.getIfNotNull(
                                                                    existingSession,
                                                                    SessionSummary::getLastSummarizedMessageId,
                                                                    null);
        final var sessionMessages = readMessagesSinceId(sessionStore,
                                                        setup,
                                                        sessionId,
                                                        lastSummarizedMessageId,
                                                        false,
                                                        messageSelectors);
        messages.add(buildSummarizationUserPrompt(context,
                                                  sessionId,
                                                  AgentUtils.getIfNotNull(
                                                                          existingSession,
                                                                          SessionSummary::getSummary,
                                                                          null),
                                                  sessionMessages));
        final var agentSetup = data.getAgentSetup();
        final var needed = existingSession == null || isCompationNeeded(data,
                                                                        messages,
                                                                        agentSetup);
        if (!needed) {
            log.debug("Summarization not needed based on current state");
            return;
        }

        final var summary = MessageCompactor.compactMessages(data.getAgent()
                .name(),
                                                             sessionId,
                                                             AgentUtils.userId(
                                                                               context),
                                                             agentSetup,
                                                             mapper,
                                                             context.getModelUsageStats(),
                                                             messages)
                .join()
                .orElse(null);
        if (null == summary) {
            log.debug("No summary extracted from the output");
        }
        else {
            // For new sessions, we do summarize, but we do not want to store the last message id
            final var newestMessageId = existingSession == null ? null
                    : sessionMessages.stream()
                            .sorted(Comparator.comparing(
                                                         AgentMessage::getTimestamp)
                                    .thenComparing(AgentMessage::getMessageId))
                            .map(AgentMessage::getMessageId)
                            .reduce((first, second) -> second)
                            .orElse(null);
            log.debug("Extracted session summary output: {}",
                      summary.getSessionSummary());
            saveSummary(sessionId,
                        context,
                        summary,
                        newestMessageId,
                        lastSummarizedMessageId);
        }
    }

}
