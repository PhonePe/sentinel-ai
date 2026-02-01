package com.phonepe.sentinelai.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.compaction.*;
import com.phonepe.sentinelai.core.agentmessages.*;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.compaction.MessageSummarizer;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.session.history.modifiers.FailedToolCallRemovalPreFilter;
import com.phonepe.sentinelai.session.history.modifiers.MessagePersistencePreFilter;
import com.phonepe.sentinelai.session.history.modifiers.SystemPromptRemovalPreFilter;
import com.phonepe.sentinelai.session.history.selectors.MessageSelector;
import com.phonepe.sentinelai.session.history.selectors.UnpairedToolCallsRemover;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;
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
            final var selected = new UnpairedToolCallsRemover().select(sessionId, agentMessages);
            final var sortedMessages = selected.stream()
                    .sorted(Comparator.comparingLong(AgentMessage::getTimestamp)
                                    .thenComparing(AgentMessage::getMessageId))
                    .toList();
            return rearrangeMessages(sortedMessages);
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
                mapper.writeValueAsString(sessionAndRunSummaryExtractionTaskPrompt(sessionId)),
                false,
                null));
        final var sessionMessages = readMessages(sessionId, setup.getMaxMessagesToSummarize(), false);
        messages.add(new UserPrompt(
                sessionId,
                context.getRunId(),
                ("Generate a %d character summary of the conversation between user and agent from the following " +
                        "messages. Messages JSON: %s")
                        .formatted(setup.getMaxSummaryLength(),
                                   mapper.writeValueAsString(sessionMessages)),
                LocalDateTime.now()));
        final var agentSetup = data.getAgentSetup();
        final var needed = isCompationNeeded(data, messages, agentSetup);
        if(!needed) {
            log.debug("Summarization not needed based on the current strategy: {}", setup.getCompactionStrategy());
            return;
        }

        final var summary = MessageSummarizer.extractSummary(
                data.getAgent().name(),
                sessionId,
                AgentUtils.userId(context),
                agentSetup,
                mapper,
                context.getModelUsageStats(),
                messages)
            .orElse(null);
            if (null == summary) {
                log.debug("No summary extracted from the output");
            }
            else {
                final var newestMessageId = sessionMessages.getItems()
                    .stream()
                    .sorted(Comparator.comparing(AgentMessage::getTimestamp)
                            .thenComparing(AgentMessage::getMessageId))
                    .map(AgentMessage::getMessageId)
                    .reduce((first, second) -> second)
                    .orElse(null);
                log.debug("Extracted session summary output: {}", summary.getSessionSummary());
                saveSummary(context, summary, newestMessageId);
            }
    }

    private boolean isCompationNeeded(Agent.ProcessingCompletedData<R, T, A> data,
            final List<AgentMessage> messages,
            final AgentSetup agentSetup) {
        return switch (setup.getCompactionStrategy()) {
            case AUTOMATIC -> {
                final var estimateTokenCount = data.getAgentSetup()
                    .getModel()
                    .estimateTokenCount(messages);
                final var contextWindowSize = agentSetup.getModelSettings()
                    .getModelAttributes()
                    .getContextWindowSize();
                final var threshold = setup.getAutoSummarizationThreshold(); 
                final var currentUsage = contextWindowSize * threshold / 100;
                final var evalResult = estimateTokenCount >= currentUsage;
                log.debug(
                    "Automatic summarization evaluation: estimatedTokenCount={}, contextWindowSize={}, " +
                        "threshold={}%, currentUsage={}, needsSummarization={}",
                    estimateTokenCount, contextWindowSize, threshold, currentUsage, evalResult);
                yield evalResult;
            }
            case EVERY_RUN -> true;
        };
    }

    private BiScrollable<AgentMessage> readMessages(
            String sessionId,
            int count,
            boolean skipSystemPrompt) {
        final var rawAccumulated = new ArrayList<AgentMessage>();
        var pointer = "";
        var filteredHistory = List.<AgentMessage>of();
        var newPointer = "";
        BiScrollable<AgentMessage> response = null;
        do {
            response = sessionStore.readMessages(
                    sessionId,
                    count,
                    skipSystemPrompt,
                    AgentUtils.getIfNotNull(response, BiScrollable::getPointer, null),
                    QueryDirection.OLDER);
            newPointer = Strings.isNullOrEmpty(newPointer) ? response.getPointer().getNewer() : newPointer;
            final var batch = response.getItems();
            pointer = response.getPointer().getOlder();
            if (batch.isEmpty()) {
                break;
            }
            rawAccumulated.addAll(batch);

            // Filter holistic chronological history
            List<AgentMessage> chronological = new ArrayList<>(rawAccumulated);
            Collections.reverse(chronological);
            for (final var filter : messageSelectors) {
                chronological = filter.select(sessionId, chronological);
            }
            filteredHistory = chronological;
        } while (filteredHistory.size() < count && !Strings.isNullOrEmpty(pointer));

        final var total = filteredHistory.size();
        final var result = List.copyOf(filteredHistory.subList(Math.max(0, total - count), total));
        return new BiScrollable<>(result, new BiScrollable.DataPointer(pointer, newPointer));
    }

    /**
     * Rearranges tool call messages to ensure that each tool call request is immediately followed by its response.
     *
     * @param outputMessages List of messages to rearrange
     * @return Rearranged list of messages
     */
    private static ArrayList<AgentMessage> rearrangeMessages(List<AgentMessage> outputMessages) {
        final var toolCallIds = groupToolCallMessages(outputMessages);
        final var rearrangedMessages = new ArrayList<AgentMessage>();
        final var processedToolCallIds = new HashSet<String>();
        for (final var message : outputMessages) {
            switch (message.getMessageType()) {
                case TOOL_CALL_REQUEST_MESSAGE, TOOL_CALL_RESPONSE_MESSAGE -> {
                    final var key = toolCallId(message);
                    if (!processedToolCallIds.contains(key) && toolCallIds.containsKey(key)) {
                        final var msgs = toolCallIds.get(key);
                        if (msgs.size() != 2) {
                            log.warn("Tool call id {} does not have both request and response. Messages: {}",
                                     key,
                                     msgs);
                        }
                        else {
                            rearrangedMessages.add(msgs.get(AgentMessageType.TOOL_CALL_REQUEST_MESSAGE));
                            rearrangedMessages.add(msgs.get(AgentMessageType.TOOL_CALL_RESPONSE_MESSAGE));
                        }
                        processedToolCallIds.add(key);
                    }
                }
                default -> rearrangedMessages.add(message);
            }
        }
        return rearrangedMessages;
    }

    /**
     * Regroups tool call messages by their tool call ids
     *
     * @param messages Message list
     * @return Map of tool call ids to their request and response messages
     */
    private static Map<String, Map<AgentMessageType, AgentMessage>> groupToolCallMessages(
            List<AgentMessage> messages) {
        //Rearrange messages to put the tool call and its response one after the other
        final var toolCallIds = new TreeMap<String, Map<AgentMessageType, AgentMessage>>();
        //Construct the map by iterating over outputMessages
        messages.forEach(outputMessage -> {
            switch (outputMessage.getMessageType()) {
                case TOOL_CALL_REQUEST_MESSAGE, TOOL_CALL_RESPONSE_MESSAGE -> {
                    final var key = toolCallId(outputMessage);
                    if (!Strings.isNullOrEmpty(key)) {
                        toolCallIds.computeIfAbsent(key, id -> new HashMap<>())
                                .put(outputMessage.getMessageType(), outputMessage);
                    }
                    else {
                        log.warn("Tool call message with empty tool call id found: {}", outputMessage);
                    }
                }
                default -> {
                    //Do nothing
                }
            }
        });
        return toolCallIds;
    }

    /**
     * Extracts tool call IDs from list of messages
     *
     * @param message Agent message
     * @return A tool call id for the message or empty string
     */
    private static String toolCallId(final AgentMessage message) {
        return message.accept(new AgentMessageVisitor<>() {
            @Override
            public String visit(AgentRequest request) {
                return request.accept(new AgentRequestVisitor<>() {

                    @Override
                    public String visit(com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt systemPrompt) {
                        return "";
                    }

                    @Override
                    public String visit(UserPrompt userPrompt) {
                        return "";
                    }

                    @Override
                    public String visit(ToolCallResponse toolCallResponse) {
                        return toolCallResponse.getToolCallId();
                    }
                });
            }

            @Override
            public String visit(AgentResponse response) {
                return response.accept(new AgentResponseVisitor<String>() {
                    @Override
                    public String visit(Text text) {
                        return "";
                    }

                    @Override
                    public String visit(StructuredOutput structuredOutput) {
                        return "";
                    }

                    @Override
                    public String visit(ToolCall toolCall) {
                        return toolCall.getToolCallId();
                    }
                });
            }

            @Override
            public String visit(AgentGenericMessage genericMessage) {
                return "";
            }
        });
    }

    private void saveSummary(AgentRunContext<R> context, final ExtractedSummary summary, String newestMessageId) {
        if (isSummaryEnabled()) {
            final var sessionId = AgentUtils.sessionId(context);
            try {
                final var updated = sessionStore.saveSession(new SessionSummary(sessionId,
                                                                                summary.getTitle(),
                                                                                summary.getSessionSummary(),
                                                                                summary.getKeywords(),
                                                                                newestMessageId,
                                                                                AgentUtils.epochMicro()))
                        .orElse(null);
                log.info("Session summary: {}", updated);
            }
            catch (Exception e) {
                log.error("Error converting json node to memory output. Error: %s Summary: %s"
                                  .formatted(e.getMessage(), summary), e);
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
