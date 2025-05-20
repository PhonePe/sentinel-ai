package com.phonepe.sentinelai.core.events;

import com.phonepe.sentinelai.core.errors.ErrorType;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import java.time.Duration;

/**
 * A tool call was completed
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ToolCallCompletedAgentEvent extends AgentEvent {

    String toolCallId;
    String toolCallName;
    ErrorType errorType;
    String errorMessage;
    Duration elapsedTime;

    public ToolCallCompletedAgentEvent(
            @NonNull String agentName,
            @NonNull String runId,
            String sessionId,
            String userId,
            @NonNull String toolCallId,
            @NonNull String toolCallName,
            ErrorType errorType,
            String errorMessage,
            @NonNull Duration elapsedTime) {
        super(EventType.TOOL_CALL_COMPLETED, agentName, runId, sessionId, userId);
        this.toolCallId = toolCallId;
        this.toolCallName = toolCallName;
        this.errorType = errorType;
        this.errorMessage = errorMessage;
        this.elapsedTime = elapsedTime;
    }

    @Override
    public <T> T accept(AgentEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
