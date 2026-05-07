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

import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Registry-based {@link MetricExecutorFactory} that dispatches to a registered
 * {@link MetricExecutorFactory} per {@link Metric} class.
 *
 * <p>Library users can extend the built-in set by calling {@link #register}:
 *
 * <pre>{@code
 * MetricExecutorRegistry registry = MetricExecutorRegistry.withDefaults(judgeModel)
 *     .register(MyMetric.class, new MetricExecutorFactory() {
 *         public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
 *                                                   ObjectMapper objectMapper,
 *                                                   ExecutorService executorService) {
 *             return new MyMetricExecutor((MyMetric<T>) metric);
 * }
 * });
 * }</pre>
 */
@Slf4j
public class MetricExecutorRegistry implements MetricExecutorFactory {

    private final Map<Class<?>, MetricExecutorFactory> registry = new ConcurrentHashMap<>();


    /**
     * Creates a registry pre-loaded with all built-in metric executors.
     */
    public static MetricExecutorRegistry withDefaults() {
        return withDefaults(null, JsonUtils.createMapper());
    }


    /**
     * Creates a registry pre-loaded with all built-in metric executors.
     *
     * @param answerRelevanceModel judge {@link Model} for answer relevance scoring;
     *                             {@code null} falls back to the built-in no-op model
     */
    public static MetricExecutorRegistry withDefaults(Model answerRelevanceModel) {
        return withDefaults(answerRelevanceModel, JsonUtils.createMapper());
    }

    /**
     * Creates a registry pre-loaded with all built-in metric executors.
     *
     * @param answerRelevanceModel judge {@link Model} for answer relevance scoring;
     *                             {@code null} falls back to the built-in no-op model
     * @param objectMapper         mapper used by JSON-dependent metric executors
     */
    public static MetricExecutorRegistry withDefaults(Model answerRelevanceModel,
                                                      ObjectMapper objectMapper) {
        final var mapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        final var registry = new MetricExecutorRegistry();
        final List<String> skippedMetrics = new ArrayList<>();

        @SuppressWarnings("unchecked") final var outputSimilarityClass = (Class<? extends Metric<?, ?>>) (Object) OutputSimilarityMetric.class;
        registry.register(outputSimilarityClass, new MetricExecutorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                                      ObjectMapper objectMapper,
                                                      ExecutorService executorService) {
                return (MetricExecutor<R, T>) new OutputSimilarityMetricExecutor<>(
                                                                                   (OutputSimilarityMetric<T>) metric);
            }
        });

        @SuppressWarnings("unchecked") final var outputRelevanceBySimilarityClass = (Class<? extends Metric<?, ?>>) (Object) OutputRelevanceBySimilarityMetric.class;
        registry.register(outputRelevanceBySimilarityClass, new MetricExecutorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                                      ObjectMapper objectMapper,
                                                      ExecutorService executorService) {
                return (MetricExecutor<R, T>) new OutputRelevanceBySimilarityMetricExecutor<>(
                                                                                              (OutputRelevanceBySimilarityMetric<T>) metric);
            }
        });

        if (answerRelevanceModel != null) {
            @SuppressWarnings("unchecked") final var outputRelevanceClass = (Class<? extends Metric<?, ?>>) (Object) OutputRelevanceMetric.class;
            registry.register(outputRelevanceClass, new MetricExecutorFactory() {
                @Override
                @SuppressWarnings("unchecked")
                public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                                          ObjectMapper objectMapper,
                                                          ExecutorService executorService) {
                    return (MetricExecutor<R, T>) new OutputRelevanceMetricExecutor<>(
                                                                                      (OutputRelevanceMetric<T>) metric,
                                                                                      answerRelevanceModel,
                                                                                      objectMapper,
                                                                                      executorService);
                }
            });
        }
        else {
            skippedMetrics.add(OutputRelevanceMetric.class.getSimpleName());
        }

        if (!skippedMetrics.isEmpty()) {
            log.warn("The following metrics were not registered: {}",
                     skippedMetrics);
        }

        return registry;
    }

    /**
     * Creates a registry pre-loaded with all built-in metric executors.
     *
     * @param objectMapper mapper used by JSON-dependent metric executors
     */
    public static MetricExecutorRegistry withDefaults(ObjectMapper objectMapper) {
        final var mapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        return withDefaults(null, mapper);
    }

    /**
     * Resolves an executor for the supplied metric instance.
     *
     * @param metric metric definition to resolve
     * @param <R>    result/output type
     * @param <T>    input/request type
     * @return executor registered for the metric class
     */
    @Override
    public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                              ObjectMapper objectMapper,
                                              ExecutorService executorService) {
        final var factory = registry.get(metric.getClass());
        if (factory == null) {
            throw new IllegalArgumentException(
                                               "No MetricExecutor registered for metric type: " + metric.getClass()
                                                       .getName()
                                                       + ". Register it via MetricExecutorRegistry.register().");
        }
        return factory.create(metric, objectMapper, executorService);
    }

    /**
     * Registers a {@link MetricExecutorFactory} for a specific {@link Metric} class.
     * Overwrites any previously registered factory for the same class.
     *
     * @param metricClass the concrete metric class to handle
     * @param factory     factory that creates executors for this metric type
     * @return this registry (fluent)
     */
    public MetricExecutorRegistry register(Class<? extends Metric<?, ?>> metricClass,
                                           MetricExecutorFactory factory) {
        Objects.requireNonNull(metricClass, "metricClass cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        registry.put(metricClass, factory);
        return this;
    }
}
