package com.phonepe.sentinelai.models.errors;


/**
 * Error signifying invalid agent messages.
 */
public class InvalidAgentMessagesException extends RuntimeException {
    public InvalidAgentMessagesException(String message) {
        super(message);
    }

    public static InvalidAgentMessagesException withMessage(String message) {
        return new InvalidAgentMessagesException(message);
    }
}
