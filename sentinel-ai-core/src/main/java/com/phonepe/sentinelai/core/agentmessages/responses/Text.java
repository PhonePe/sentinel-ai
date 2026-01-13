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

    public Text(
            String sessionId,
            String runId,
            @NonNull String content) {
        this(sessionId,
             runId,
             null,
             null,
             content);
    }

    @Builder
    @Jacksonized
    public Text(
            String sessionId,
            String runId,
            String messageId,
            Long timestamp,
            @NonNull String content) {
        super(AgentMessageType.TEXT_RESPONSE_MESSAGE, sessionId, runId, messageId, timestamp);
        this.content = content;
    }

    @Override
    public <T> T accept(AgentResponseVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
