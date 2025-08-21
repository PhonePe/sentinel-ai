package com.phonepe.sentinelai.core.outputvalidation;

import com.phonepe.sentinelai.core.agent.AgentRunContext;

/**
 * Does not validate anything by default
 */
public class DefaultOutputValidator<R,T> implements OutputValidator<R,T> {
    @Override
    public OutputValidationResults validate(AgentRunContext<R> context, T modelOutput) {
        return OutputValidationResults.success();
    }

}
