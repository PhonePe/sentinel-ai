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
public class StructuredOutput extends AgentResponse {
    /**
     * Serialized content for structured output
     */
    String content;

    @Builder
    @Jacksonized
    public StructuredOutput(@NonNull String content) {
        super(AgentMessageType.STRUCTURED_OUTPUT_RESPONSE_MESSAGE);
        this.content = content;
    }

    @Override
    public <T> T accept(AgentResponseVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
