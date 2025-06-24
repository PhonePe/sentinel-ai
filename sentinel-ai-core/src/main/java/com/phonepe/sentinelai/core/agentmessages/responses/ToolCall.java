package com.phonepe.sentinelai.core.agentmessages.responses;

import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.AgentResponse;
import com.phonepe.sentinelai.core.agentmessages.AgentResponseVisitor;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

/**
 * Text response from LLM
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ToolCall extends AgentResponse {
    /**
     * Tool call id as received from LLM
     */
    String toolCallId;

    /**
     * Tool name for the tool to be called
     */
    String toolName;

    /**
     * Serialized arguments
     */
    String arguments;

    @Builder
    @Jacksonized
    public ToolCall(@NonNull String toolCallId, @NonNull String toolName, String arguments) {
        super(AgentMessageType.TOOL_CALL_REQUEST_MESSAGE);
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.arguments = arguments;
    }

    @Override
    public <T> T accept(AgentResponseVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
