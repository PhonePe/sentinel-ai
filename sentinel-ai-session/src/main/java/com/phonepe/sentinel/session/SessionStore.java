package com.phonepe.sentinel.session;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.List;
import java.util.Optional;

/**
 * A storage system for agent session
 */
public interface SessionStore {
    Optional<SessionSummary> session(String sessionId);

    List<SessionSummary> sessions(String agentName);

    Optional<SessionSummary> saveSession(String agentName, SessionSummary sessionSummary);

    void saveMessages(String sessionId, String runId, List<AgentMessage> messages);

    List<AgentMessage> readMessages(String sessionId, int count, boolean skipSystemPrompt);

}
