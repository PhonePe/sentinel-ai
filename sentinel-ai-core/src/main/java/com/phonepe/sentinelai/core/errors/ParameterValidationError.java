package com.phonepe.sentinelai.core.errors;

/**
 * Validation failures in the parameters passed to agents and other components
 */
public class ParameterValidationError extends RuntimeException {
    public ParameterValidationError(final String message) {
        super(message);
    }
}
