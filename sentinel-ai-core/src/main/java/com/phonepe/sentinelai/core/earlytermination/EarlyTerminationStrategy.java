package com.phonepe.sentinelai.core.earlytermination;

import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;

import java.util.Optional;

/**
 * Strategy to determine if a model run should be terminated early
 */

@FunctionalInterface
public interface EarlyTerminationStrategy {

    Optional<ModelOutput> shouldTerminateEarly(ModelSettings modelSettings, ModelRunContext modelRunContext);
}
