package com.phonepe.sentinelai.core.earlytermination;

import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;

/**
 * An early termination strategy that never terminates the model run early.
 */


public class NeverTerminateEarlyStrategy implements EarlyTerminationStrategy {
    @Override
    public EarlyTerminationStrategyResponse evaluate(final ModelSettings modelSettings,final ModelRunContext context, final ModelOutput output) {
        return EarlyTerminationStrategyResponse.doNotTerminate();
    }
}
