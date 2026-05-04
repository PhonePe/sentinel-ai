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

/**
 * A metric calculates a numeric score (0.0-1.0) for evaluating quality aspects.
 *
 * Metrics are standalone and independent of expectations. They measure something
 * and produce a score. The score can then be:
 * - Reported without enforcement (just for informational purposes)
 * - Checked against a threshold to determine pass/fail
 *
 * @param <R> The result/output type being evaluated
 * @param <T> The input/request type that generated the result
 */
public interface Metric<R, T> {
    /**
     * Calculate a metric score for the given result.
     *
     * @param result  the output/result to evaluate
     * @param context evaluation context containing request, messages, and usage stats
     * @return a score between 0.0 and 1.0 (inclusive)
     */
    double calculate(R result, EvalExpectationContext<T> context);

    /**
     * Human-readable name of this metric.
     *
     * @return metric name
     */
    default String metricName() {
        return getClass().getSimpleName();
    }
}
