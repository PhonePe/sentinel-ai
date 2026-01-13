package com.phonepe.sentinelai.core.agentmessages.requests;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GenericText extends AgentGenericMessage {

    String text;

    public GenericText(
            String sessionId,
            String runId,
            Role role,
            String text) {
        this(sessionId,
             runId,
             null,
             null,
             role,
             text);
    }

    @Builder
    @Jacksonized
    public GenericText(
            String sessionId,
            String runId,
            String messageId,
            Long timestamp,
            Role role,
            String text) {
        super(sessionId, runId, messageId, timestamp, AgentMessageType.GENERIC_TEXT_MESSAGE, role);
        this.text = text;
    }

    @Override
    public <T> T accept(AgentGenericMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
