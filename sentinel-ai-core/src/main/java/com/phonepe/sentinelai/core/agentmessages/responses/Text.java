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
public class Text extends AgentResponse {
    /**
     * Text content
     */
    String content;

    @Builder
    @Jacksonized
    public Text(@NonNull String content) {
        super(AgentMessageType.TEXT_RESPONSE_MESSAGE);
        this.content = content;
    }

    @Override
    public <T> T accept(AgentResponseVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
