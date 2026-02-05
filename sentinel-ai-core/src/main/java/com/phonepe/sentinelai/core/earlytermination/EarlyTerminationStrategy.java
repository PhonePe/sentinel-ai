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
     *
     * @param modelSettings   Model Settings
     * @param modelRunContext Model run context containing run-specific information
     * @param output          Current Model Output
     * @return EarlyTerminationStrategyResponse indicating whether to terminate early along with error type and reason
     */
    EarlyTerminationStrategyResponse evaluate(final ModelSettings modelSettings, final ModelRunContext modelRunContext,
            final ModelOutput output);
}
