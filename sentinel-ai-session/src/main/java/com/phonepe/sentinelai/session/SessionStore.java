package com.phonepe.sentinelai.session;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.List;
import java.util.Optional;

/**
 * A storage system for agent session
 */
public interface SessionStore {
    Optional<SessionSummary> session(String sessionId);

    ScrollableResponse<SessionSummary> sessions(int count, String pointer, QueryDirection queryDirection);

    boolean deleteSession(String sessionId);

    Optional<SessionSummary> saveSession(SessionSummary sessionSummary);

    void saveMessages(String sessionId, String runId, List<AgentMessage> messages);

    ScrollableResponse<AgentMessage> readMessages(String sessionId, int count, boolean skipSystemPrompt, String pointer, QueryDirection queryDirection);

}
