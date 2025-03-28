package com.phonepe.sentinelai.core.agent;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Optional metadata that can be sent to agent.
 */
@Data
@Builder
public class AgentRequestMetadata {
    private String sessionId;
    private String userId;
    private Map<String, Object> optionalParams;
}
