package com.phonepe.sentinelai.core.model;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import lombok.Value;

/**
 * A context object passed to the model at runtime.
 */
@Value
public class ModelRunContext {
    /**
     * Name of the agent that is running this model
     */
    String agentName;
    /**
     * An id for this particular run. This is used to track the run in logs and events
     */
    String runId;

    /**
     * Session id for this run. This is used to track the run in logs and events
     */
    String sessionId;

    /**
     * User id for this run. This is used to track the run in logs and events
     */
    String userId;

    /**
     * Required setup for the agent
     */
    AgentSetup agentSetup;

    /**
     * Model usage stats for this run
     */
    ModelUsageStats modelUsageStats;

    /**
     * Processing mode for this run
     */
    ProcessingMode processingMode;
}
