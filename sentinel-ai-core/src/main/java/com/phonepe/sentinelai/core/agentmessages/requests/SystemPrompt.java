package com.phonepe.sentinelai.core.agentmessages.requests;

import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

/**
 * System prompt sent by user
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SystemPrompt extends AgentRequest {
    /**
     * Content of the main system prompt
     */
    String content;

    /**
     * Whether the system prompt is generated dynamically
     */
    boolean dynamic;

    /**
     * Name of the method used to generate the system prompt
     */
    String methodReference;

    @Builder
    @Jacksonized
    public SystemPrompt(@NonNull String content, boolean dynamic, String methodReference) {
        super(AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE);
        this.content = content;
        this.dynamic = dynamic;
        this.methodReference = methodReference;
    }

    @Override
    public <T> T accept(AgentRequestVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
