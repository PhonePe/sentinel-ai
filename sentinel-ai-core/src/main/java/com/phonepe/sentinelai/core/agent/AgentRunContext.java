package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelSettings;
import lombok.Value;
import lombok.With;

import java.util.List;

/**
 * Context injected into the agent at runtime. This remains constant for a particular agent run
 */
@Value
@With
public class AgentRunContext<D, R> {
    /**
     * Unique session id for the agent run
     */
    String sessionId;

    /**
     * Dependencies for the agent
     */
    D dependencies;

    /**
     * Model being used for the agent
     */
    Model model;

    /**
     * Model settings
     */
    ModelSettings modelSettings;

    /**
     * old messages
     */
    List<AgentMessage> oldMessages;

    /**
     * Request
     */
    R request;


    /**
     * Model usage stats for this run
     */
    ModelUsageStats modelUsageStats;

}
