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

import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;

import lombok.RequiredArgsConstructor;
import lombok.val;

@RequiredArgsConstructor
public class CostMetricExecutor<R, T> implements MetricExecutor<R, T> {

    private final CostCalculator costCalculator;

    @Override
    public double calculate(R result, EvalExpectationContext<T> context) {
        val usage = context.getModelUsageStats();
        if (usage == null) {
            return 0.0;
        }
        return costCalculator.calculate(context.getModelId(), usage);
    }

    @Override
    public String metricName() {
        return "Cost";
    }
}
