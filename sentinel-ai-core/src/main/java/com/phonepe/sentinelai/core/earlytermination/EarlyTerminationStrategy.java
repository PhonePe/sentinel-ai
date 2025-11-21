package com.phonepe.sentinelai.core.earlytermination;

import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelSettings;

/**
 * Strategy to determine if an agent run should be terminated early
 */

@FunctionalInterface
public interface EarlyTerminationStrategy {

    /**
     * Evaluate whether to terminate the model run early
     * @param modelSettings Model Settings
     * @param modelRunContext Model run context containing run-specific information
     * @param output Current Model Output
     * @return EarlyTerminationStrategyResponse indicating whether to terminate early along with error type and reason
     */
    EarlyTerminationStrategyResponse evaluate(final ModelSettings modelSettings,final ModelRunContext modelRunContext,final ModelOutput output);
}
