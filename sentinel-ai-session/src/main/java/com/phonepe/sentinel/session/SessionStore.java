package com.phonepe.sentinel.session;

import java.util.Optional;

/**
 * A storage system for agent session
 */
public interface SessionStore {
    Optional<SessionSummary> session(String sessionId);
    Optional<SessionSummary> saveSession(SessionSummary sessionSummary);
}
