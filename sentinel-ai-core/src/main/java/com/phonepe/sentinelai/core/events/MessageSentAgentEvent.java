package com.phonepe.sentinelai.core.events;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 * A message was sent from the agent to the LLM
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MessageSentAgentEvent extends AgentEvent {
    List<AgentMessage> messages;

    @Builder
    @Jacksonized
    public MessageSentAgentEvent(
            @NonNull String agentName,
            @NonNull String runId,
            String sessionId,
            String userId,
            @NonNull List<AgentMessage> messages) {
        super(EventType.MESSAGE_SENT, agentName, runId, sessionId, userId);
        this.messages = messages;
    }

    @Override
    public <T> T accept(AgentEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
