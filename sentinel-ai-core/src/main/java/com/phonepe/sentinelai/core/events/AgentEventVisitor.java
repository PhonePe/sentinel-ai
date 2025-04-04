package com.phonepe.sentinelai.core.events;

/**
 * Interface for visiting agent event subclasses to implement type specific behaviour
 */
public interface AgentEventVisitor<T> {
    T visit(MessageReceivedAgentEvent messageReceived);

    T visit(MessageSentAgentEvent messageSent);

    T visit(ToolCalledAgentEvent toolCalled);

    T visit(ToolCallCompletedAgentEvent toolCallCompleted);
}
