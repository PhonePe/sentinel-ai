package com.phonepe.sentinelai.core.errors;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 */
@Getter
@AllArgsConstructor
public enum ErrorType {
    SUCCESS("Success", false),
    NO_RESPONSE("No response", true),
    REFUSED("Refused: Reason: %s", false),
    FILTERED("Content filtered", false),
    LENGTH_EXCEEDED("Content length exceeded", false),
    TOOL_CALL_PERMANENT_FAILURE("Tool call failed permanently for tool: %s", false),
    TOOL_CALL_TEMPORARY_FAILURE("Tool call failed temporarily for tool: %s", true),
    JSON_ERROR("Error parsing JSON. Error: %s", true),
    SERIALIZATION_ERROR("Error serializing object to JSON. Error: %s", true),
    DESERIALIZATION_ERROR("Error deserializing object to JSON. Error: %s", true),
    UNKNOWN_FINISH_REASON("Unknown finish reason: %s", true),
    GENERIC_MODEL_CALL_FAILURE("Model call failed with error: %s", true),
    DATA_VALIDATION_FAILURE("Model data validation failed. Errors: %s", true),
    FORCED_RETRY("Retry has been forced", true),
    TIMEOUT("Request timed out", true),
    COMMUNICATION_ERROR("Network error", true),
    UNKNOWN("Unknown response", true),
    MODEL_RUN_TERMINATED("Model run was terminated", false),
    ;

    private final String message;
    private final boolean retryable;
}
