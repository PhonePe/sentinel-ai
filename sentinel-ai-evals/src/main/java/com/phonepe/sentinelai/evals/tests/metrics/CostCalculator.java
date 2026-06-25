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

package com.phonepe.sentinelai.evals.tests.metrics;

import com.phonepe.sentinelai.core.model.ModelUsageStats;

/**
 * Computes the monetary cost of a single inference call.
 */
@FunctionalInterface
public interface CostCalculator {

    /**
     * Returns a no-op calculator that always reports zero cost.
     *
     * @return a cost calculator that returns {@code 0.0}
     */
    static CostCalculator noOp() {
        return (modelId, usage) -> 0.0;
    }

    /**
     * Calculates the cost for a given model and usage.
     *
     * @param modelId model identifier used for the inference call
     * @param usage   token usage stats from the call
     * @return computed cost
     */
    double calculate(String modelId, ModelUsageStats usage);
}
