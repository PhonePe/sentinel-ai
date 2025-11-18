package com.phonepe.sentinelai.core.earlytermination;

import com.phonepe.sentinelai.core.errors.ErrorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EarlyTerminationStrategyResponse {

    private boolean shouldTerminate;
    private ErrorType errorType;
    private String reason;
}
