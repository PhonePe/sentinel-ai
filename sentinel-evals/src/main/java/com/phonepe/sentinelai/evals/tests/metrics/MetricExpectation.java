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

import com.phonepe.sentinelai.evals.tests.Expectation;

import lombok.ToString;

/**
 * Expectation definition that wraps a {@link Metric} with an optional pass/fail threshold.
 *
 * Computation is performed by {@code MetricExpectationExecutor}.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
@ToString
public class MetricExpectation<R, T> implements Expectation<R, T> {

    private final Metric<R, T> metric;
    private final Double threshold;

    public MetricExpectation(Metric<R, T> metric) {
        this(metric, null);
    }

    public MetricExpectation(Metric<R, T> metric, Double threshold) {
        this.metric = metric;
        this.threshold = threshold;
    }

    public Metric<R, T> getMetric() {
        return metric;
    }

    public Double getThreshold() {
        return threshold;
    }
}
