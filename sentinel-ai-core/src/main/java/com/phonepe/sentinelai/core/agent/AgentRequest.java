package com.phonepe.sentinelai.core.agent;

import lombok.Data;

import java.util.UUID;

/**
 *
 */
@Data
public abstract class AgentRequest {
    private final String requestId;
    private final String userPrompt;

    protected AgentRequest(String userPrompt) {
        this(UUID.randomUUID().toString(), userPrompt);
    }

    protected AgentRequest(String requestId, String userPrompt) {
        this.requestId = requestId;
        this.userPrompt = userPrompt;
    }
}
