package com.phonepe.sentinelai.core.agentmessages;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Base class for all requests sent by the agent to the LLM
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class AgentGenericMessage extends AgentMessage {

    public enum Role {
        SYSTEM, // System message
        USER,   // User message
        ASSISTANT, // Assistant message
        TOOL_CALL // Tool call message
    }

    private final Role role;

    protected AgentGenericMessage(AgentMessageType messageType, Role role) {
        super(messageType);
        this.role = role;
    }

    @Override
    public <T> T accept(AgentMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public abstract <T> T accept(AgentGenericMessageVisitor<T> visitor);
}
