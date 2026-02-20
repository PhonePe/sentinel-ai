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
import com.phonepe.sentinelai.core.compaction.CompactionPrompts;
import com.phonepe.sentinelai.core.compaction.ExtractedSummary;
import com.phonepe.sentinelai.core.compaction.MessageCompactor;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.events.AgentEvent;
import com.phonepe.sentinelai.core.events.EventType;
import com.phonepe.sentinelai.core.events.OutputErrorAgentEvent;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
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
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import static com.phonepe.sentinelai.session.MessageReadingUtils.readMessagesSinceId;
import static com.phonepe.sentinelai.session.MessageReadingUtils.rearrangeMessages;
import static com.phonepe.sentinelai.session.internal.SessionUtils.isAlreadyLengthExceeded;
import static com.phonepe.sentinelai.session.internal.SessionUtils.isContextWindowThresholdBreached;


/**
 * Manages session for an agent. Saves the message history and summarizes the session after each run.
 * Injects session summary as fact in the system prompt. Also provides messages from the session history to the agent.
 */
@Slf4j
@Getter(value = AccessLevel.PACKAGE, onMethod_ = {
        @VisibleForTesting
})
public class AgentSessionExtension<R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {
    private static final String COMPACTION_SESSION_PREFIX = "session-compaction-for-";

    private static final String OUTPUT_KEY = "sessionOutput";

    private final ObjectMapper mapper;
    private final SessionStore sessionStore;
    private final AgentSessionExtensionSetup setup;
    private final List<MessagePersistencePreFilter> historyModifiers;
    private final List<MessageSelector> messageSelectors;
    private final Set<EventType> compactionTriggeringEvents;
    private final AgentEventMessageExtractor extractor = new AgentEventMessageExtractor();
    private A agent;

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
                                 List<MessagePersistencePreFilter> historyModifiers,
                                 List<MessageSelector> messageSelectors) {
        this.mapper = Objects.requireNonNullElseGet(mapper,
                                                    JsonUtils::createMapper);
        this.setup = Objects.requireNonNullElse(setup,
                                                AgentSessionExtensionSetup.DEFAULT);
        this.sessionStore = sessionStore;
        this.historyModifiers = new CopyOnWriteArrayList<>(Objects
                .requireNonNullElseGet(historyModifiers,
                                       () -> List.of(
                                                     new SystemPromptRemovalPreFilter(),
                                                     new FailedToolCallRemovalPreFilter())));
        this.messageSelectors = new CopyOnWriteArrayList<>(Objects
                .requireNonNullElseGet(messageSelectors,
                                       () -> List.of(
                                                     new UnpairedToolCallsRemover())));
        this.compactionTriggeringEvents = Objects.requireNonNullElse(
                                                                     this.setup.getCompactionTriggeringEvents(),
                                                                     AgentSessionExtensionSetup.DEFAULT_COMPACTION_TRIGGERING_EVENTS);
    }

    private static String compactionSessionId(String sessionId) {
        return COMPACTION_SESSION_PREFIX + sessionId;
    }

    public AgentSessionExtension<R, T, A> addMessagePersistencePreFilter(MessagePersistencePreFilter modifier) {
        return addMessagePersistencePreFilters(List.of(modifier));
    }

    public AgentSessionExtension<R, T, A> addMessagePersistencePreFilters(List<MessagePersistencePreFilter> modifiers) {
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
                                                                                     .getRaw())))))
                .orElse(List.of());
    }


    /**
     * Forces compaction for a given session.
     * This can be used to manually trigger summarization and reduce the session history size. Uses the current agent
     * setup for summarization.
     *
     * @param sessionId Session Id for which compaction is to be forced
     * @return CompletableFuture containing the updated session summary after compaction
     */
    public CompletableFuture<Optional<SessionSummary>> forceCompaction(@NonNull String sessionId) {
        return forceCompaction(sessionId, null);
    }

    /**
     * Forces compaction for a given session.
     * This can be used to manually trigger summarization and reduce the session history size.
     *
     * @param sessionId          Session Id for which compaction is to be forced
     * @param providedAgentSetup Agent setup to be used for summarization. If null, current agent setup will be used.
     * @return CompletableFuture containing the updated session summary after compaction
     */
    public CompletableFuture<Optional<SessionSummary>> forceCompaction(@NonNull String sessionId,
                                                                       AgentSetup providedAgentSetup) {
        return CompletableFuture.supplyAsync(() -> {
            final var runId = "manual-compaction-run-" + AgentUtils.epochMicro();
            final var agentStup = Objects.requireNonNullElse(providedAgentSetup, agent.getSetup());
            try {
                summarizeConversation(sessionId, runId, null, agentStup, null, true);
                return sessionStore.session(sessionId);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Forced compaction interrupted for session: {}", sessionId);
                return Optional.empty();
            }
            catch (Exception e) {
                log.error("Error during forced compaction for session %s: %s"
                        .formatted(sessionId, AgentUtils.rootCause(e).getMessage()), e);
                return Optional.empty();
            }
        }, agent.getSetup().getExecutorService());
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

    // We want guaranteed message saving and inline summarization if needded
    // This also means that the first call of a session will get summarized
    // This helps set the title of the session can can be used for display purposes
    @Override
    public void onExtensionRegistrationCompleted(A agent) {
        this.agent = agent;
        agent.onRequestCompleted().connect(this::summarizeConversation);
        agent.getSetup().getEventBus()
                .onEventBlocking() // Blocks the loop till work is done loop till work is done loop till work is done loop till work is done
                .connect(this::processEvent);
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

    private SystemPrompt.Task buildSummarizationSystemPrompt(String sessionId) {
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
    private UserPrompt buildSummarizationUserPrompt(final String sessionId,
                                                    final String runId,
                                                    final String currentSummary,
                                                    final List<AgentMessage> sessionMessages)
            throws JsonProcessingException {
        return new UserPrompt(sessionId,
                              runId,
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

    /*
     * This method processes events and takes action as needed
     * Actions taken:
     * - If events are for session extraction itself it will ignore the changes
     * - Run {@ling AgentEventMessageExtractor} to see if event contains any messages
     * - In case messages are present they get saved
     * - Calls summarizeConversationImpl to compact messages if needed
     */
    private void processEvent(AgentEvent event) {
        final var sessionId = event.getSessionId();
        if (sessionId.startsWith(COMPACTION_SESSION_PREFIX)) {
            log.debug("Skipping messages for compaction itself");
            return;
        }
        log.debug("Event {} ({})", event.getEventId(), event.getType());
        final var extractedData = event.accept(extractor).orElse(null);
        if (null == extractedData || extractedData.getNewMessages().isEmpty()) {
            log.debug("No messages from event {} of type {}", event.getEventId(), event.getType());
            return;
        }
        final var runId = event.getRunId();
        final var newMessages = extractedData.getNewMessages();
        if (!saveMessages(sessionId, runId, newMessages)) {
            log.debug("No new messages for event: {}", event.getType());
            return;
        }
        else {
            log.debug("Messages saved for event {} ({})", event.getEventId(), event.getType());
        }
        log.debug("SESSION ID {}", event.getSessionId(), event);

        var compactionNeeded = false;

        // There are some cases where we do not care about whitelisted events

        // Did we get length exceeded? then we need to compact
        // We don't honour the whitelisted events list. You don't get to take a call if the system is
        // already crying about context window length. We need to compact in that case to be able to continue the conversation.
        if (event.getType() == EventType.OUTPUT_ERROR) {
            if (event instanceof OutputErrorAgentEvent errorEvent
                    && errorEvent.getErrorType().equals(ErrorType.LENGTH_EXCEEDED)) {
                log.debug("Compaction will be forced as we have received LENGTH_EXCEEDED for session: {}",
                          sessionId);
                compactionNeeded = true;
            }
        }
        // Check messages to see if context window threshdold is breached
        final var agentSetup = agent.getSetup();
        if (!compactionNeeded) {
            if (compactionTriggeringEvents.contains(event.getType())) {
                if (isContextWindowThresholdBreached(extractedData.getAllMessages(), agentSetup, setup)) {
                    log.info("Context window threshold breached. Will compact session: {}", sessionId);
                    compactionNeeded = true;
                }
            }
            else {
                log.debug("Event type {} is not in compaction triggering events, skipping compaction evaluation for session: {}",
                          event.getType(),
                          sessionId);
            }
        }
        //If there is no session saved we do one quick compaction to populate a summary
        if (!compactionNeeded) {
            if (sessionStore.session(sessionId).isEmpty()) {
                log.debug("There is no session saved, we use this opportunity to do a small compation");
                compactionNeeded = true;
            }
        }

        if (compactionNeeded) {
            try {
                summarizeConversation(sessionId,
                                      runId,
                                      null, // Let it compact everything since last summary
                                      agentSetup,
                                      null,
                                      true);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("Summarization interrupted for session: {}", sessionId);
            }
            catch (Exception e) {
                log.error("Failed to summariize session %s: %s"
                        .formatted(sessionId, AgentUtils.rootCause(e).getMessage()),
                          e);
            }
        }
        else {
            log.debug("No compaction needed for session: {}", sessionId);
        }
    }

    /*
     * - Runs modifiers to remove messages we do not wat to save (by default system messages)
     * - Saves messages
     */
    private boolean saveMessages(String sessionId,
                                 String runId,
                                 List<AgentMessage> messages) {
        if (log.isDebugEnabled()) {
            log.debug("Going to save messages for session {}: {}",
                      sessionId,
                      messages.stream()
                              .map(AgentMessage::getMessageId)
                              .toList());
        }
        var newMessages = messages;
        for (var modifier : historyModifiers) {
            newMessages = modifier.filter(newMessages);
        }
        if (newMessages.isEmpty()) {
            log.warn("No new messages to save after applying modifiers");
            return false;
        }
        sessionStore.saveMessages(sessionId, runId, newMessages);
        if (log.isDebugEnabled()) {
            log.debug("Saved messages for session {}: {}",
                      sessionId,
                      newMessages.stream()
                              .map(AgentMessage::getMessageId)
                              .toList());
        }
        else {
            log.info("Saved {} messages for session {}", newMessages.size(), sessionId);
        }
        return true;
    }

    private void saveSummary(final String sessionId,
                             final ExtractedSummary summary,
                             final String newestMessageId,
                             final String lastSummarizedMessageId) {
        try {
            final var existingSessionMessageId = sessionStore.session(sessionId)
                    .map(SessionSummary::getLastSummarizedMessageId)
                    .orElse(null);
            if (lastSummarizedMessageId != null
                    && !Objects.equals(lastSummarizedMessageId, existingSessionMessageId)) {
                log.warn("Skipping summary save as the newest message id {} does not match existing session's last summarized message id {} for session: {}",
                         lastSummarizedMessageId,
                         existingSessionMessageId,
                         sessionId);
                return;
            }
            final var updated = sessionStore.saveSession(SessionSummary
                    .builder()
                    .sessionId(sessionId)
                    .title(summary.getTitle())
                    .summary(summary.getSummary())
                    .keywords(summary.getKeywords())
                    .raw(mapper.writeValueAsString(summary.getRawData()))
                    .lastSummarizedMessageId(newestMessageId)
                    .updatedAt(AgentUtils.epochMicro())
                    .build()).orElse(null);
            log.debug("Session summary: {}", updated);
        }
        catch (Exception e) {
            log.error("Error converting json node to memory output. Error: %s Summary: %s"
                    .formatted(e.getMessage(), newestMessageId), e);
        }
    }

    /*
     * This is used to trigger async compaction if needed _after_ the response is needed.
     * It checks if LLM has responsed with a
     */
    private void summarizeConversation(Agent.ProcessingCompletedData<R, T, A> data) {
        try {
            final var context = data.getContext();
            summarizeConversation(AgentUtils.sessionId(context),
                                  context.getRunId(),
                                  null,
                                  context.getAgentSetup(),
                                  context.getModelUsageStats(),
                                  isAlreadyLengthExceeded(data));
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

    private void summarizeConversation(String sessionId,
                                       String runId,
                                       List<AgentMessage> messagesToSummarize,
                                       AgentSetup agentSetup,
                                       ModelUsageStats modelUsageStats,
                                       boolean force)
            throws JsonProcessingException,
            InterruptedException, ExecutionException {
        final var existingSession = sessionStore.session(sessionId)
                .orElse(null);
        final var messages = new ArrayList<AgentMessage>();
        messages.add(new com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt(sessionId,
                                                                                         runId,
                                                                                         mapper.writeValueAsString(buildSummarizationSystemPrompt(sessionId)),
                                                                                         false,
                                                                                         null));
        final var lastSummarizedMessageId = AgentUtils.getIfNotNull(
                                                                    existingSession,
                                                                    SessionSummary::getLastSummarizedMessageId,
                                                                    null);
        final var sessionMessages = Objects.requireNonNullElseGet(messagesToSummarize,
                                                                  () -> readMessagesSinceId(sessionStore,
                                                                                            setup,
                                                                                            sessionId,
                                                                                            lastSummarizedMessageId,
                                                                                            false,
                                                                                            messageSelectors));
        messages.add(buildSummarizationUserPrompt(sessionId,
                                                  runId,
                                                  AgentUtils.getIfNotNull(
                                                                          existingSession,
                                                                          SessionSummary::getSummary,
                                                                          null),
                                                  sessionMessages));
        final var title = AgentUtils.getIfNotNull(existingSession, SessionSummary::getTitle, null);
        final var needed = Strings.isNullOrEmpty(title)
                || force
                || isContextWindowThresholdBreached(messagesToSummarize, agentSetup, setup);
        if (!needed) {
            log.debug("Summarization not needed based on current state");
            return;
        }

        final var stats = Objects.requireNonNullElseGet(modelUsageStats, ModelUsageStats::new);

        final var summary = MessageCompactor.compactMessages(agent.name(),
                                                             compactionSessionId(sessionId),
                                                             null,
                                                             agentSetup,
                                                             mapper,
                                                             stats,
                                                             sessionMessages,
                                                             Objects.requireNonNullElse(setup
                                                                     .getCompactionPrompts(),
                                                                                        CompactionPrompts.DEFAULT),
                                                             setup.getMaxSummaryLength())
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
                      summary.getSummary());
            saveSummary(sessionId,
                        summary,
                        newestMessageId,
                        lastSummarizedMessageId);
        }
    }

}
