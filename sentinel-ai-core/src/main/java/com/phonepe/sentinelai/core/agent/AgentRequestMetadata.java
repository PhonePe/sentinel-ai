package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.model.ModelUsageStats;
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
    private ModelUsageStats usageStats;
}
