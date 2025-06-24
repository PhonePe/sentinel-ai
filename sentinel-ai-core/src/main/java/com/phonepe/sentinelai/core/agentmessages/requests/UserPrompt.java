package com.phonepe.sentinelai.core.agentmessages.requests;

import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * User prompt/request sent from user to LLM
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserPrompt extends AgentRequest {
    String content;
    LocalDateTime sentAt;

    @Builder
    @Jacksonized
    public UserPrompt(@NonNull String content, LocalDateTime sentAt) {
        super(AgentMessageType.USER_PROMPT_REQUEST_MESSAGE);
        this.content = content;
        this.sentAt = Objects.requireNonNullElse(sentAt, LocalDateTime.now());
    }

    @Override
    public <T> T accept(AgentRequestVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
