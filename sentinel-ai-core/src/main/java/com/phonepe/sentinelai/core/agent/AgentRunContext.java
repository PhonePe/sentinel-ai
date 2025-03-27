package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import lombok.Value;
import lombok.With;

import java.util.List;

/**
 * Context injected into the agent at runtime. This remains constant for a particular agent run
 */
@Value
@With
public class AgentRunContext<R> {
    /**
     * An id for this particular run. This is used to track the run in logs and events
     */
    String runId;
    /**
     * Request
     */
    R request;

    /**
     * Metadata for the request
     */
    AgentRequestMetadata requestMetadata;

    /**
     * Required setup for the agent
     */
    AgentSetup agentSetup;

    /**
     * Dependencies for the agent
     */
    Object dependencies;

    /**
     * old messages
     */
    List<AgentMessage> oldMessages;

    /**
     * Model usage stats for this run
     */
    ModelUsageStats modelUsageStats;

}
