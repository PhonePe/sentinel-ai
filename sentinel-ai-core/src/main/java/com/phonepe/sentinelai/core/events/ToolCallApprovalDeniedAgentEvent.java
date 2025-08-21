package com.phonepe.sentinelai.core.events;

import lombok.*;
import lombok.extern.jackson.Jacksonized;

/**
 * A tool call has been requested by the LLM
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ToolCallApprovalDeniedAgentEvent extends AgentEvent {
    String toolCallId;
    String toolCallName;

    @Builder
    @Jacksonized
    public ToolCallApprovalDeniedAgentEvent(
            @NonNull String agentName,
            @NonNull String runId,
            String sessionId,
            String userId,
            @NonNull String toolCallId,
            @NonNull String toolCallName) {
        super(EventType.TOOL_CALL_APPROVAL_DENIED, agentName, runId, sessionId, userId);
        this.toolCallId = toolCallId;
        this.toolCallName = toolCallName;
    }

    @Override
    public <T> T accept(AgentEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
