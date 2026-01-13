package com.phonepe.sentinel.session;

import com.phonepe.sentinel.session.history.selectors.MessageSelector;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A session store that selects messages using a list of message selectors.
 * It will keep fetching messages from the root store until the required count is met
 */
public class SelectingSessionStore implements SessionStore {
    private final SessionStore root;
    private final List<MessageSelector> messageSelectors;

    public  SelectingSessionStore(SessionStore root) {
        this(root, new ArrayList<>());
    }

    public SelectingSessionStore(
            @NonNull SessionStore root,
            @NonNull List<MessageSelector> messageSelectors) {
        this.root = root;
        this.messageSelectors = messageSelectors;
    }

    public SelectingSessionStore registerSelectors(@NonNull List<MessageSelector> selectors) {
        this.messageSelectors.addAll(selectors);
        return this;
    }

    public SelectingSessionStore registerSelectors(@NonNull MessageSelector... selectors) {
        this.messageSelectors.addAll(List.of(selectors));
        return this;
    }

    @Override
    public Optional<SessionSummary> session(String sessionId) {
        return root.session(sessionId);
    }

    @Override
    public ListResponse<SessionSummary> sessions(int count, String nextPagePointer) {
        return root.sessions(count, nextPagePointer);
    }

    @Override
    public boolean deleteSession(String sessionId) {
        return root.deleteSession(sessionId);
    }

    @Override
    public Optional<SessionSummary> saveSession(String agentName, SessionSummary sessionSummary) {
        return root.saveSession(agentName, sessionSummary);
    }

    @Override
    public void saveMessages(String sessionId, String runId, List<AgentMessage> messages) {
        root.saveMessages(sessionId, runId, messages);
    }

    @Override
    public ListResponse<AgentMessage> readMessages(
            String sessionId,
            int count,
            boolean skipSystemPrompt,
            String nextPointer) {
        int totalMessagesCount = 0;
        var outputMessages = new ArrayList<AgentMessage>();
        var pointer = nextPointer;
        do {
            final var response = root.readMessages(sessionId, count, skipSystemPrompt, pointer);
            var messages = response.getItems();
            pointer = response.getNextPageToken();
            if (messages.isEmpty()) {
                break;
            }
            for (final var filter : messageSelectors) {
                messages = filter.select(sessionId, messages);
            }
            totalMessagesCount += messages.size();
            outputMessages.addAll(messages);
        } while (totalMessagesCount < count);
        return new ListResponse<>(outputMessages, pointer);
    }
}
