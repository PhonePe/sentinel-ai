package com.phonepe.sentinelai.core.agentmessages;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Responses as received from LLM
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class AgentResponse extends AgentMessage {

    protected AgentResponse(AgentMessageType messageType,
                            String sessionId,
                            String runId,
                            String messageId,
                            Long timestamp) {
        super(messageType, sessionId, runId, messageId, timestamp);
    }

    @Override
    public <T> T accept(AgentMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public abstract <T> T accept(AgentResponseVisitor<T> visitor);
}
