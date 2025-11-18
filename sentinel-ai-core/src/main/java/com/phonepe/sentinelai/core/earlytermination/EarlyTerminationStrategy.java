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

    /**
     * Evaluate whether to terminate the model run early
     * @param modelSettings Model Settings
     * @param modelRunContext Model run context containing run-specific information
     * @return Optional containing ModelOutput if the run should be terminated early, empty otherwise
     */
    Optional<ModelOutput> evaluate(ModelSettings modelSettings, ModelRunContext modelRunContext);
}
