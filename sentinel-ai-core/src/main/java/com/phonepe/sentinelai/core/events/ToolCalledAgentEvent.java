package com.phonepe.sentinelai.core.events;

import lombok.*;
import lombok.extern.jackson.Jacksonized;

/**
 * A tool call has been requested by the LLM
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ToolCalledAgentEvent extends AgentEvent {
    String toolCallId;
    String toolCallName;

    @Builder
    @Jacksonized
    public ToolCalledAgentEvent(
            @NonNull String agentName,
            @NonNull String runId,
            String sessionId,
            String userId,
            @NonNull String toolCallId,
            @NonNull String toolCallName) {
        super(EventType.TOOL_CALLED, agentName, runId, sessionId, userId);
        this.toolCallId = toolCallId;
        this.toolCallName = toolCallName;
    }

    @Override
    public <T> T accept(AgentEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
