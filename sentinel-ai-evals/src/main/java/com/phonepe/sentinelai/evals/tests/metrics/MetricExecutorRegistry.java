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
import com.phonepe.sentinelai.embedding.EmbeddingModel;

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
 * MetricExecutorRegistry registry = MetricExecutorRegistry.withDefaults(
 *         new EmbeddingModelIdentifier("text-embedding-3-small"),
 *         id -> new HuggingfaceEmbeddingModel(id.modelId(), ...),
 *         new LLMIdentifier("gpt-4o"),
 *         id -> new SimpleOpenAIModel<>(id.modelId(), provider, mapper, options))
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
     * Metrics requiring an embedding model or LLM judge will not be registered.
     *
     * <p>This is a convenience overload that uses {@link EmbeddingModelFactory#noOp()} and
     * {@link LLMModelFactory#noOp()} as defaults, causing all model-dependent metrics to be
     * skipped with a warning.
     */
    public static MetricExecutorRegistry withDefaults() {
        return withDefaults(null,
                            EmbeddingModelFactory.noOp(),
                            null,
                            LLMModelFactory.noOp(),
                            JsonUtils.createMapper());
    }

    /**
     * Creates a registry pre-loaded with all built-in metric executors.
     *
     * @param embeddingModel       {@link EmbeddingModel} for embedding-based metrics;
     *                             {@code null} skips registering embedding-based metrics
     * @param answerRelevanceModel judge {@link Model} for answer relevance scoring;
     *                             {@code null} skips registering LLM-judge metrics
     * @param objectMapper         mapper used by JSON-dependent metric executors
     */
    public static MetricExecutorRegistry withDefaults(EmbeddingModel embeddingModel,
                                                      Model answerRelevanceModel,
                                                      ObjectMapper objectMapper) {
        return buildRegistry(embeddingModel, answerRelevanceModel, objectMapper);
    }

    /**
     * Creates a registry pre-loaded with all built-in metric executors, resolving models
     * via the supplied factories and identifiers.
     *
     * @param embeddingModelIdentifier identifies the embedding model passed to {@code embeddingModelFactory};
     *                                 {@code null} skips embedding-based metrics
     * @param embeddingModelFactory    factory that creates an {@link EmbeddingModel} from the identifier;
     *                                 uses {@link EmbeddingModelFactory#noOp()} when {@code null}
     * @param llmIdentifier            identifies the LLM passed to {@code llmModelFactory};
     *                                 {@code null} skips LLM-judge metrics
     * @param llmModelFactory          factory that creates a {@link Model} from the identifier;
     *                                 uses {@link LLMModelFactory#noOp()} when {@code null}
     */
    public static MetricExecutorRegistry withDefaults(EmbeddingModelIdentifier embeddingModelIdentifier,
                                                      EmbeddingModelFactory embeddingModelFactory,
                                                      LLMIdentifier llmIdentifier,
                                                      LLMModelFactory llmModelFactory) {
        return withDefaults(embeddingModelIdentifier,
                            embeddingModelFactory,
                            llmIdentifier,
                            llmModelFactory,
                            JsonUtils.createMapper());
    }

    /**
     * Creates a registry pre-loaded with all built-in metric executors, resolving models
     * via the supplied factories and identifiers.
     *
     * @param embeddingModelIdentifier identifies the embedding model passed to {@code embeddingModelFactory};
     *                                 {@code null} skips embedding-based metrics
     * @param embeddingModelFactory    factory that creates an {@link EmbeddingModel} from the identifier;
     *                                 uses {@link EmbeddingModelFactory#noOp()} when {@code null}
     * @param llmIdentifier            identifies the LLM passed to {@code llmModelFactory};
     *                                 {@code null} skips LLM-judge metrics
     * @param llmModelFactory          factory that creates a {@link Model} from the identifier;
     *                                 uses {@link LLMModelFactory#noOp()} when {@code null}
     * @param objectMapper             mapper used by JSON-dependent metric executors
     */
    public static MetricExecutorRegistry withDefaults(EmbeddingModelIdentifier embeddingModelIdentifier,
                                                      EmbeddingModelFactory embeddingModelFactory,
                                                      LLMIdentifier llmIdentifier,
                                                      LLMModelFactory llmModelFactory,
                                                      ObjectMapper objectMapper) {
        final var effectiveEmbeddingFactory = Objects.requireNonNullElse(embeddingModelFactory,
                                                                         EmbeddingModelFactory.noOp());
        final var effectiveLlmFactory = Objects.requireNonNullElse(llmModelFactory, LLMModelFactory.noOp());

        final EmbeddingModel embeddingModel = embeddingModelIdentifier != null
                ? effectiveEmbeddingFactory.create(embeddingModelIdentifier) : null;
        final Model answerRelevanceModel = llmIdentifier != null
                ? effectiveLlmFactory.create(llmIdentifier) : null;

        return buildRegistry(embeddingModel, answerRelevanceModel, objectMapper);
    }

    private static MetricExecutorRegistry buildRegistry(EmbeddingModel embeddingModel,
                                                        Model answerRelevanceModel,
                                                        ObjectMapper objectMapper) {
        Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        final var registry = new MetricExecutorRegistry();
        final List<String> skippedMetrics = new ArrayList<>();

        if (embeddingModel != null) {
            registry.registerMetric(OutputSimilarityMetric.class, new MetricExecutorFactory() {
                @Override
                @SuppressWarnings("unchecked")
                public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                                          ObjectMapper objectMapper,
                                                          ExecutorService executorService) {
                    final var typedMetric = (OutputSimilarityMetric<T>) metric;
                    return (MetricExecutor<R, T>) new OutputSimilarityMetricExecutor<>(typedMetric, embeddingModel);
                }
            });

            registry.registerMetric(OutputRelevanceBySimilarityMetric.class, new MetricExecutorFactory() {
                @Override
                @SuppressWarnings("unchecked")
                public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                                          ObjectMapper objectMapper,
                                                          ExecutorService executorService) {
                    final var typedMetric = (OutputRelevanceBySimilarityMetric<T>) metric;
                    return (MetricExecutor<R, T>) new OutputRelevanceBySimilarityMetricExecutor<>(typedMetric,
                                                                                                  embeddingModel);
                }
            });
        }
        else {
            skippedMetrics.add(OutputSimilarityMetric.class.getSimpleName());
            skippedMetrics.add(OutputRelevanceBySimilarityMetric.class.getSimpleName());
        }

        if (answerRelevanceModel != null) {
            registry.registerMetric(OutputRelevanceMetric.class, new MetricExecutorFactory() {
                @Override
                @SuppressWarnings("unchecked")
                public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                                          ObjectMapper objectMapper,
                                                          ExecutorService executorService) {
                    final var typedMetric = (OutputRelevanceMetric<T>) metric;
                    return (MetricExecutor<R, T>) new OutputRelevanceMetricExecutor<>(typedMetric,
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
            log.warn("The following metrics were not registered due to missing model dependencies: {}",
                     skippedMetrics);
        }

        return registry;
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

    private <M extends Metric<?, ?>> void registerMetric(Class<M> metricClass, MetricExecutorFactory factory) {
        Objects.requireNonNull(metricClass, "metricClass cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        register((Class<? extends Metric<?, ?>>) (Object) metricClass, factory);
    }
}
