package com.phonepe.sentinelai.core.earlytermination;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;

public class NeverTerminateEarly implements EarlyTerminationHandler{
    @Override
    public boolean shouldTerminateEarly(ModelSettings modelSettings, ModelRunContext context) {
        return false;
    }
}
