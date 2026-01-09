package com.phonepe.sentinelai.core.agentmessages.requests;

import com.phonepe.sentinelai.core.agentmessages.*;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

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
        super(sessionId, runId, AgentMessageType.GENERIC_TEXT_MESSAGE, role);
        this.text = text;
    }

    @Override
    public <T> T accept(AgentGenericMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
