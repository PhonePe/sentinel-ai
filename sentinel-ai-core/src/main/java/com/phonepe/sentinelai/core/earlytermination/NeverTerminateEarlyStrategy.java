package com.phonepe.sentinelai.core.earlytermination;

import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;

import java.util.Optional;

/**
 * An early termination strategy that never terminates the model run early.
 */


public class NeverTerminateEarlyStrategy implements EarlyTerminationStrategy {
    @Override
    public Optional<ModelOutput> shouldTerminateEarly(ModelSettings modelSettings, ModelRunContext context) {
        return Optional.empty();
    }
}
