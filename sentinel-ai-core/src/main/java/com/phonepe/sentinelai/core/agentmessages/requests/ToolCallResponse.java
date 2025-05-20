package com.phonepe.sentinelai.core.agentmessages.requests;

import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import com.phonepe.sentinelai.core.errors.ErrorType;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Response for a tool run
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ToolCallResponse extends AgentRequest {

    /**
     * Tool call ID as received from the LLM
     */
    String toolCallId;

    /**
     * Name of the tool that was called
     */
    String toolName;

    /**
     * Serialized response that was sent to model
     */
    String response;

    /**
     * boolean to indicate if the response is a success or failure
     */
    ErrorType errorType;

    /**
     * Call time
     */
    LocalDateTime sentAt;

    @Builder
    @Jacksonized
    public ToolCallResponse(
            @NonNull String toolCallId,
            @NonNull String toolName,
            ErrorType errorType,
            @NonNull String response,
            LocalDateTime sentAt) {
        super(AgentMessageType.TOOL_CALL_RESPONSE);
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.errorType = errorType;
        this.response = response;
        this.sentAt = Objects.requireNonNullElse(sentAt, LocalDateTime.now());
    }

    @Override
    public <T> T accept(AgentRequestVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public boolean isSuccess() {
        return errorType.equals(ErrorType.SUCCESS);
    }
}
