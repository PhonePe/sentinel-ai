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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;

import io.appform.signals.signals.ConsumingFireForgetSignal;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.Fact;
import com.phonepe.sentinelai.core.agent.FactList;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import static com.phonepe.sentinelai.session.MessageReadingUtils.readMessagesSinceId;
import static com.phonepe.sentinelai.session.MessageReadingUtils.rearrangeMessages;


/**
 * Manages session for an agent. Saves the message history and summarizes the session after each run.
 * Injects session summary as fact in the system prompt. Also provides messages from the session history to the agent.
 */
@Slf4j
@Getter(value = AccessLevel.PACKAGE, onMethod_ = {
        @VisibleForTesting
})
public class AgentSessionExtension<R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {

    private final ObjectMapper mapper;
    private final SessionStore sessionStore;
    private final AgentSessionExtensionSetup setup;
    private final List<MessagePersistencePreFilter> historyModifiers;
    private final List<MessageSelector> messageSelectors;
    private final AgentEventMessageExtractor extractor = new AgentEventMessageExtractor();
    private final ConsumingFireForgetSignal<SessionSummary> onSessionSummarized = new ConsumingFireForgetSignal<>();
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
        final var agentSetup = Objects.requireNonNullElse(providedAgentSetup, agent.getSetup());
        return CompletableFuture.supplyAsync(() -> {
            try {
                final var existingSession = sessionStore.session(sessionId)
                        .orElse(null);
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
                log.info("Read {} messages for summarization for session: {}", sessionMessages.size(), sessionId);
                summarizeConversation(sessionId, agentSetup, sessionMessages);
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
        }, agentSetup.getExecutorService());
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
        log.info("Read {} messages for session {}", agentMessages.size(), sessionId);
        if (agentMessages.isEmpty()) {
            log.info("No messages found for session {}", sessionId);
            return List.of();
        }
        return rearrangeMessages(new UnpairedToolCallsRemover()
                .select(sessionId, agentMessages));
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
        agent.getSetup().getEventBus()
                .onEventBlocking() // Blocks the loop till work is done loop till work is done
                .connect(this::processEvent);
    }

    public ConsumingFireForgetSignal<SessionSummary> onSessionSummarized() {
        return onSessionSummarized;
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

    /*
     * This method processes events and takes action as needed
     * Actions taken:
     * - If events are for session extraction itself it will ignore the changes
     * - Run {@link AgentEventMessageExtractor} to see if event contains any messages
     * - In case messages are present they get saved
     * - Calls summarizeConversationImpl to compact messages if needed
     */
    @SuppressWarnings("java:S3776")
    private void processEvent(AgentEvent event) {
        try {
            final var sessionId = event.getSessionId();
            if (Strings.isNullOrEmpty(sessionId)) {
                log.debug("No session id found in event {}, skipping event processing", event.getEventId());
                return;
            }
            if (sessionId.startsWith(MessageCompactor.COMPACTION_SESSION_PREFIX)) {
                log.debug("Skipping messages for compaction itself");
                return;
            }
            log.debug("Event {} ({})", event.getEventId(), event.getType());
            final var runId = event.getRunId();
            final var extractedData = event.accept(extractor).orElse(null);
            if (null == extractedData || extractedData.getNewMessages().isEmpty()) {
                log.debug("No messages from event {} of type {}", event.getEventId(), event.getType());
                return;
            }
            final var newMessages = extractedData.getNewMessages();
            if (!saveMessages(sessionId, runId, newMessages)) {
                log.debug("No new messages for event: {}", event.getType());
                return;
            }
            else {
                log.debug("Messages saved for event {} ({})", event.getEventId(), event.getType());
            }
            final var summary = sessionStore.session(sessionId).orElse(null);
            log.debug("Current session summary for session {}: {}", sessionId, summary);
            if (null == summary) {
                if (setup.isPreSummarizationDisabled()) {
                    log.debug("Pre-summarization is disabled. Skipping summarization for session {}", sessionId);
                    return;
                }
                final var userPrompt = newMessages.stream()
                        .filter(message -> message.getMessageType() == AgentMessageType.USER_PROMPT_REQUEST_MESSAGE)
                        .map(UserPrompt.class::cast)
                        .findAny()
                        .orElse(null);
                if (null == userPrompt) {
                    log.debug("No user prompt found in messages for session {}. Skipping summarization.", sessionId);
                    return;
                }
                log.info("Starting first summarization for session: {}", sessionId);
                try {
                    summarizeConversation(sessionId, agent.getSetup(), List.of(userPrompt));
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
                if (event.getType().equals(EventType.OUTPUT_ERROR)
                        && event instanceof OutputErrorAgentEvent errorEvent
                        && errorEvent.getErrorType().equals(ErrorType.LENGTH_EXCEEDED)) {
                    log.info("Starting emergency summarization for session: {}", sessionId);
                    forceCompaction(sessionId);
                }
                else {
                    log.debug("Summary exists for session {}.", sessionId);
                    final var lastSummarizedMessageId = newMessages.stream()
                            .filter(message -> message.getMessageType() == AgentMessageType.USER_PROMPT_REQUEST_MESSAGE)
                            .filter(message -> message instanceof UserPrompt userPrompt && userPrompt.isCompacted())
                            .map(AgentMessage::getMessageId)
                            .reduce((first, second) -> second)
                            .orElse(null);
                    if (null != lastSummarizedMessageId) {
                        log.debug("Compaction triggered by message id {} for session {} as it is marked compacted",
                                  lastSummarizedMessageId,
                                  sessionId);

                        final var updated = sessionStore.saveSession(SessionSummary
                                .builder()
                                .sessionId(sessionId)
                                .title(summary.getTitle())
                                .summary(summary.getSummary())
                                .keywords(summary.getKeywords())
                                .raw(summary.getRaw())
                                .lastSummarizedMessageId(lastSummarizedMessageId)
                                .updatedAt(AgentUtils.epochMicro())
                                .build());
                        updated.ifPresentOrElse(
                                                savedSummary -> log.info(
                                                                         "Summary saved successfully for session: {}. Title: {}",
                                                                         sessionId,
                                                                         savedSummary.getTitle()),
                                                () -> log.error("Failed to save summary for session: {}", sessionId));
                    }
                    else {
                        log.debug("Auto compaction has not yet been triggered for session {}", sessionId);
                    }
                }
            }

        }
        catch (Exception e) {
            log.error("Error while processing event %s for session %s: %s"
                    .formatted(event.getType(), event.getSessionId(), AgentUtils.rootCause(e).getMessage()), e);
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

    @SneakyThrows
    private void summarizeConversation(String sessionId,
                                       AgentSetup agentSetup,
                                       List<AgentMessage> sessionMessages)
            throws InterruptedException, ExecutionException {
        final var stats = new ModelUsageStats();
        final var compactionSetup = agentSetup.getAutoCompactionSetup();

        final var prompts = Objects.requireNonNullElse(compactionSetup.getPrompts(),
                                                       CompactionPrompts.DEFAULT);
        final var summary = MessageCompactor.compactMessages(agent.name(),
                                                             sessionId,
                                                             null,
                                                             agentSetup,
                                                             mapper,
                                                             stats,
                                                             sessionMessages,
                                                             prompts,
                                                             compactionSetup.getTokenBudget())
                .join()
                .orElse(null);
        if (null == summary) {
            log.debug("No summary extracted from the output");
        }
        else {
            log.debug("Session summary extracted: {}", summary);
            final var updated = sessionStore.saveSession(SessionSummary
                    .builder()
                    .sessionId(sessionId)
                    .title(summary.getTitle())
                    .summary(summary.getSummary())
                    .keywords(summary.getKeywords())
                    .raw(mapper.writeValueAsString(summary.getRawData()))
                    .lastSummarizedMessageId(null)
                    .updatedAt(AgentUtils.epochMicro())
                    .build());
            updated.ifPresentOrElse(
                                    savedSummary -> log.info("Summary saved successfully for session: {}. Title: {}",
                                                             sessionId,
                                                             savedSummary.getTitle()),
                                    () -> log.error("Failed to save summary for session: {}", sessionId));
        }
    }

}
