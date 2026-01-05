package com.phonepe.sentinelai.core.hooks;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.List;

/**
 * Context passed to an agent messages pre-process handler.
 */
@Value
@Builder
@With
public class AgentMessagesPreProcessContext {
    /**
     * The context of the model run, which includes metadata about the agent and the request.
     */
    ModelRunContext modelRunContext;
}
