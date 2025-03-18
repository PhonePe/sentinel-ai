package com.phonepe.sentinelai.core.events;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.time.Duration;

/**
 * A response was received from the LLM
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MessageReceivedAgentEvent extends AgentEvent {
    AgentMessage message;
    Duration elapsedTime;

    @Builder
    @Jacksonized
    public MessageReceivedAgentEvent(
            @NonNull String agentName,
            @NonNull String runId,
            String sessionId,
            String userId,
            @NonNull AgentMessage message,
            @NonNull Duration elapsedTime) {
        super(EventType.MESSAGE_RECEIVED, agentName, runId, sessionId, userId);
        this.message = message;
        this.elapsedTime = elapsedTime;
    }

    @Override
    public <T> T accept(AgentEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
