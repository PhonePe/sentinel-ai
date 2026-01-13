package com.phonepe.sentinel.session;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import lombok.Value;

import java.util.List;
import java.util.Optional;

/**
 * A storage system for agent session
 */
public interface SessionStore {
    @Value
    class ListResponse<T> {
        List<T> items;
        String nextPageToken;
    }

    Optional<SessionSummary> session(String sessionId);

    ListResponse<SessionSummary> sessions(int count, String nextPagePointer);

    boolean deleteSession(String sessionId);

    Optional<SessionSummary> saveSession(String agentName, SessionSummary sessionSummary);

    void saveMessages(String sessionId, String runId, List<AgentMessage> messages);

    ListResponse<AgentMessage> readMessages(String sessionId, int count, boolean skipSystemPrompt, String nextPointer);

}
