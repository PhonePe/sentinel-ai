package com.phonepe.sentinelai.session;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.List;
import java.util.Optional;

/**
 * A storage system for agent session
 */
public interface SessionStore {
    Optional<SessionSummary> session(String sessionId);

    BiScrollable<SessionSummary> sessions(int count, String pointer, QueryDirection queryDirection);

    boolean deleteSession(String sessionId);

    Optional<SessionSummary> saveSession(SessionSummary sessionSummary);

    void saveMessages(String sessionId, String runId, List<AgentMessage> messages);

    /**
     * Reads messages for a specific session with pagination support. This method helps clients keep their state management
     * simpler. Basically the same {@link BiScrollable} can be passed back to the server to get the next set of messages
     * in both directions.
     * Message Ordering:
     *  - Messages are chronologically sorted older to newer if {@code queryDirection} is {@link QueryDirection#OLDER}
     *  - Messages are chronologically sorted newer to older if {@code queryDirection} is {@link QueryDirection#NEWER}
     *
     * @param sessionId        The unique identifier for the session.
     * @param count            The maximum number of messages to retrieve.
     * @param skipSystemPrompt If true, system prompt request messages will be excluded from the result.
     * @param pointer          The {@link BiScrollable} used by the client to indicate the current position in
     *                         the message list.
     * @param queryDirection   The direction to scroll in: {@link QueryDirection#OLDER} to fetch messages before the
     *                         pointer,
     *                         or {@link QueryDirection#NEWER} to fetch messages after the pointer.
     * @return A {@link BiScrollable} containing the list of messages (sorted chronologically) and pointers for
     * further scrolling.
     */
    BiScrollable<AgentMessage> readMessages(
            String sessionId,
            int count,
            boolean skipSystemPrompt,
            BiScrollable<AgentMessage> pointer,
            QueryDirection queryDirection);

}
