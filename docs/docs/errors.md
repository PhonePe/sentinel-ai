---
title: Error Handling & Error Types
description: Understanding and handling errors in Sentinel AI
---

# Error Handling

Sentinel AI uses a structured error handling approach. When an agent execution fails, it returns a `SentinelError` object which contains an `ErrorType` and a descriptive message.

## The `ErrorType` Enum

The `ErrorType` enum defines the categories of errors that can occur during agent execution. Each error type also indicates whether it is **retryable**.

| Error Type | Description | Retryable |
|------------|-------------|-----------|
| `SUCCESS` | Operation completed successfully. | No |
| `NO_RESPONSE` | No response was received from the model. | Yes |
| `REFUSED` | The model refused to generate a response (e.g., safety filters). | No |
| `FILTERED` | The generated content was filtered by the provider. | No |
| `LENGTH_EXCEEDED` | The generated content exceeded the maximum allowed length/tokens. | No |
| `TOOL_CALL_PERMANENT_FAILURE` | A tool call failed with a non-recoverable error. | No |
| `TOOL_CALL_TEMPORARY_FAILURE` | A tool call failed with a transient error. | Yes |
| `TOOL_CALL_TIMEOUT` | A tool call exceeded its configured timeout. | Yes |
| `JSON_ERROR` | Error parsing JSON response from the model. | Yes |
| `SERIALIZATION_ERROR` | Error serializing request or tool arguments to JSON. | Yes |
| `DESERIALIZATION_ERROR` | Error deserializing model response or tool results. | Yes |
| `UNKNOWN_FINISH_REASON` | The model stopped for an unrecognized reason. | Yes |
| `GENERIC_MODEL_CALL_FAILURE` | An unspecified error occurred during the model call. | Yes |
| `DATA_VALIDATION_FAILURE` | The generated output failed validation against the output schema. | Yes |
| `FORCED_RETRY` | A retry was explicitly triggered (e.g., by an output validator). | Yes |
| `MODEL_CALL_COMMUNICATION_ERROR` | Network or communication error with the model provider. | Yes |
| `MODEL_CALL_RATE_LIMIT_EXCEEDED` | The model provider's rate limit was exceeded. | Yes |
| `MODEL_CALL_HTTP_FAILURE` | The HTTP call to the model provider failed with a non-2xx status code. | Yes |
| `PREPROCESSOR_RUN_FAILURE` | An error occurred while running a message pre-processor. | Yes |
| `PREPROCESSOR_MESSAGES_OUTPUT_INVALID` | A pre-processor returned invalid or null messages. | No |
| `MODEL_RUN_TERMINATED` | The model run was terminated by an early termination strategy. | No |
| `UNKNOWN` | An unexpected error occurred. | Yes |

## Handling Errors in Code

When you execute an agent, you should check the `isSuccessful()` method of the `AgentOutput`.

```java
final var response = agent.execute(input);

if (response.isSuccessful()) {
    System.out.println("Result: " + response.getData());
} else {
    SentinelError error = response.getError();
    System.err.println("Error Type: " + error.getErrorType());
    System.err.println("Message: " + error.getMessage());
    
    if (error.getErrorType().isRetryable()) {
        // Optionally implement custom retry logic if not using RetrySetup
    }
}
```

## Retry Configuration

As discussed in the [Tool Retries & Timeouts](tools.md#tool-retries-timeouts) section, you can configure automatic retries for specific error types using `RetrySetup`.

```java
final var retrySetup = RetrySetup.builder()
        .totalAttempts(3)
        .retriableErrorTypes(Set.of(ErrorType.MODEL_CALL_COMMUNICATION_ERROR, ErrorType.MODEL_CALL_RATE_LIMIT_EXCEEDED))
        .build();
```
