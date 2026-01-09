package com.phonepe.sentinel.session.storage;

import com.phonepe.sentinel.session.SessionSummary;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 * Persistent form of {@link SessionSummary}
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PersistentSessionSummary extends PersistentObject {
    SessionSummary summary;

    public PersistentSessionSummary(
            final SessionSummary summary) {
        super(SessionStoredObjectType.SESSION_SUMMARY,
              AgentUtils.id("session", summary.getSessionId()),
              summary.getSessionId(),
              null,
              AgentUtils.epochMicro());
        this.summary = summary;
    }

    @Override
    public <T> T accept(SessionStoredObjectVisitor<T> visitor) {
        return null;
    }
}
