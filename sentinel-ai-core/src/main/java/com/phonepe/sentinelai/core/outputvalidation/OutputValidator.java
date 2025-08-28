package com.phonepe.sentinelai.core.outputvalidation;

import com.phonepe.sentinelai.core.agent.AgentRunContext;

/**
 * Validates output from model
 */
@FunctionalInterface
public interface OutputValidator<R,T> {
    OutputValidationResults validate(AgentRunContext<R> context, T modelOutput);
}
