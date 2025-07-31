package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.model.ModelUsageStats;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Optional metadata that can be sent to agent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequestMetadata {
    /**
     * Session ID for the current conversation. This is passed to LLM as additional data in system prompt.
     */
    private String sessionId;
    /**
     * A User ID for the user the agent is having the current conversation with. This is passed to LLM as additional
     * data in system prompt.
     */
    private String userId;

    /**
     * Any other custom parameters that need to be passed to the agent or the tools being invoked by the agent. This is
     * passed to LLM as additional data in system prompt.
     */
    private Map<String, Object> customParams;

    /**
     * Global usage stats object that can be used to track usage of the model across execute calls.
     */
    private ModelUsageStats usageStats;
}
