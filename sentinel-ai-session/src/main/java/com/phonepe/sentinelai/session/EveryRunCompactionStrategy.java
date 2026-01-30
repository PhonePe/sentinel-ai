package com.phonepe.sentinelai.session;

import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.errors.ErrorType;


/**
 * Return true if run was successful.
 */
public class EveryRunCompactionStrategy implements CompactionStrategy {

    @Override
    public boolean isCompactionNeeded(AgentRunContext<?> context, AgentOutput<?> output) {
        return output.getError().getErrorType().equals(ErrorType.SUCCESS);
    }

}
