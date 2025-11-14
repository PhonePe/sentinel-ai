package com.phonepe.sentinelai.core.earlytermination;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;

@FunctionalInterface
public interface EarlyTerminationHandler {

    boolean shouldTerminateEarly(ModelSettings modelSettings, ModelRunContext modelRunContext);
}
