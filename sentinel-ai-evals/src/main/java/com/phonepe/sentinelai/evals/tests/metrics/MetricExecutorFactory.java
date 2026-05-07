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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;

/**
 * Abstract factory that creates a {@link MetricExecutor} for a given {@link Metric} definition.
 *
 * Implementations determine which executor type corresponds to each metric type, enabling
 * the definition (what to measure) and execution (how to compute) to evolve independently.
 */
public interface MetricExecutorFactory {

    /**
     * Create an executor capable of computing the score described by the given metric.
     *
     * @param metric          the metric definition
     * @param objectMapper    mapper supplied by the eval engine for JSON operations
     * @param executorService executor supplied by the eval engine for async work
     * @param <R>             result/output type
     * @param <T>             input/request type
     * @return a {@link MetricExecutor} for the supplied metric
     * @throws IllegalArgumentException if no executor is registered for the metric type
     */
    <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                       ObjectMapper objectMapper,
                                       ExecutorService executorService);
}
