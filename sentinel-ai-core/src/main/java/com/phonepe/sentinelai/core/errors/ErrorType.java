package com.phonepe.sentinelai.core.errors;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 */
@Getter
@AllArgsConstructor
public enum ErrorType {
    SUCCESS("Success"),
    NO_RESPONSE("No response"),
    REFUSED("Refused: Reason: %s"),
    FILTERED("Content filtered"),
    LENGTH_EXCEEDED("Content length exceeded"),
    TOOL_CALL_PERMANENT_FAILURE("Tool call failed permanently for tools: %s"),
    JSON_ERROR("Error parsing JSON. Error: %s"),
    SERIALIZATION_ERROR("Error serializing object to JSON. Error: %s"),
    UNKNOWN("Unknown response"),
    ;

    private final String message;
    private final boolean retryable = false;
}
