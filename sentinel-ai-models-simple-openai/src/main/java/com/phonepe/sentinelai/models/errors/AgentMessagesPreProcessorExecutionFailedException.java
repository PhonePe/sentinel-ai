package com.phonepe.sentinelai.models.errors;


/**
 * Agent Messages pre-processor failure.
 */
public class AgentMessagesPreProcessorExecutionFailedException extends RuntimeException {
    public AgentMessagesPreProcessorExecutionFailedException(final String message, final Throwable t) {
        super(message, t);
    }
}
