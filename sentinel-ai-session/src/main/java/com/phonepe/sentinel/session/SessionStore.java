package com.phonepe.sentinel.session;

import java.util.List;
import java.util.Optional;

/**
 * A storage system for agent session
 */
public interface SessionStore {
    Optional<SessionSummary> session(String sessionId);

    List<SessionSummary> sessions(String agentName);

    Optional<SessionSummary> saveSession(String agentName, SessionSummary sessionSummary);
}
