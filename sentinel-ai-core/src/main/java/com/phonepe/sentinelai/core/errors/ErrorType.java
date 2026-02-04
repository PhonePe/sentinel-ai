/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    MODEL_CALL_COMMUNICATION_ERROR("Network error", true),
    MODEL_CALL_RATE_LIMIT_EXCEEDED("Rate limit exceeded: %s", true),
    MODEL_CALL_HTTP_FAILURE("Error making HTTP Call: %s", true),
    UNKNOWN("Unknown response", true),
    MODEL_RUN_TERMINATED("Model run was terminated", false),
    PREPROCESSOR_RUN_FAILURE("Agent messages preprocessor failed: %s", true),
    PREPROCESSOR_MESSAGES_OUTPUT_INVALID("Invalid output from Pre-Processor: %s", false)
    ;

    private final String message;
    private final boolean retryable;
}
