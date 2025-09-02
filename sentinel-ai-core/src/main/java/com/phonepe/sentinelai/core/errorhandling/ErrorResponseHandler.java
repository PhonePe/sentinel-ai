package com.phonepe.sentinelai.core.errorhandling;

import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;

/**
 * Can be used to do custom error handling
 */
@FunctionalInterface
public interface ErrorResponseHandler<R> {

    <U> AgentOutput<U> handle(AgentRunContext<R> context, AgentOutput<U> agentOutput);
}
