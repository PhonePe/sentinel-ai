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

/**
 * Defines the identity and configuration of a metric that measures a quality aspect.
 *
 * A Metric is a pure definition: it carries the parameters (e.g. model, thresholds,
 * reference text) that describe WHAT to measure. The actual computation is performed
 * by a corresponding {@link MetricExecutor} created via a {@link MetricExecutorFactory}.
 *
 * The score range is 0.0–1.0 (inclusive).
 *
 * @param <R> The result/output type being evaluated
 * @param <T> The input/request type that generated the result
 */
public interface Metric<R, T> {

    /**
     * Human-readable name of this metric.
     *
     * @return metric name
     */
    default String metricName() {
        return getClass().getSimpleName();
    }
}
