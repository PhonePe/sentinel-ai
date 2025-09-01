package com.phonepe.sentinelai.core.errorhandling;

import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;

/**
 * Returns the error response as is
 */
public class DefaultErrorHandler<R> implements ErrorResponseHandler<R> {

    @Override
    public <U> AgentOutput<U> handle(AgentRunContext<R> context, AgentOutput<U> agentOutput) {
        return agentOutput;
    }
}
