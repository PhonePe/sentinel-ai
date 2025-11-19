package com.phonepe.sentinelai.core.earlytermination;

import com.phonepe.sentinelai.core.errors.ErrorType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;


@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EarlyTerminationStrategyResponse {

    ResponseType responseType;
    ErrorType errorType;
    String reason;

    public static EarlyTerminationStrategyResponse terminate(ErrorType errorType, String reason) {
        return new EarlyTerminationStrategyResponse(ResponseType.TERMINATE, errorType, reason);
    }

    public static EarlyTerminationStrategyResponse doNotTerminate() {
        return new EarlyTerminationStrategyResponse(ResponseType.CONTINUE, null, null);
    }

    public enum ResponseType {
        TERMINATE,
        CONTINUE
    }
}
