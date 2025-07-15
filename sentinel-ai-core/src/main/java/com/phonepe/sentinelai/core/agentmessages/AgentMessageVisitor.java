package com.phonepe.sentinelai.core.agentmessages;

/**
 * Top level visitor to handle all agent messages
 */
public interface AgentMessageVisitor<T> {
    T visit(AgentRequest request);

    T visit(AgentResponse response);

    T visit(AgentGenericMessage genericMessage);
}
