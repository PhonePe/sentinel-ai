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
    String sessionId;
    String userId;
    Map<String, Object> optionalParams;
}
