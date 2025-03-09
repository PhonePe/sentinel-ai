package com.phonepe.sentinelai.core.errors;

import lombok.Value;

/**
 * Error from model execution
 */
@Value
public class SentinelError {
    ErrorType errorType;
    String message;

    public static SentinelError success() {
        return new SentinelError(ErrorType.SUCCESS, ErrorType.SUCCESS.getMessage());
    }

    public static SentinelError error(ErrorType errorType, Object ... args) {
        return new SentinelError(errorType, String.format(errorType.getMessage(), args));
    }

    public static SentinelError error(ErrorType errorType, Throwable throwable) {
        var cause = throwable.getCause();
        var message = throwable.getMessage();
        do {
            if (cause != null) {
                message = cause.getMessage();
                cause = cause.getCause();
            }
        } while (cause != null);
        return SentinelError.error(errorType, message);
    }

}
