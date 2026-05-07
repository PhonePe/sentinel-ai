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
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
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
public class MetricExecutorRegistry implements MetricExecutorFactory {

    private static final Model DEFAULT_ANSWER_RELEVANCE_MODEL = new DefaultAnswerRelevanceModel();

    private static final class DefaultAnswerRelevanceModel implements Model {
        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            final var data = JsonNodeFactory.instance.objectNode();
            data.put(Agent.OUTPUT_VARIABLE_NAME,
                     "{\"score\":0.0,\"reason\":\"No answer relevance evaluator model configured\"}");
            final var safeMessages = oldMessages == null ? List.<AgentMessage>of() : oldMessages;
            return CompletableFuture.completedFuture(ModelOutput.success(data,
                                                                         List.of(),
                                                                         safeMessages,
                                                                         new ModelUsageStats()));
        }
    }

    private final Map<Class<?>, MetricExecutorFactory> registry = new LinkedHashMap<>();

    /**
     * Creates a registry pre-loaded with all built-in metric executors, using a no-op judge model
     * for answer relevance (score will always be 0.0 unless a real model is configured).
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
        final var judgeModel = answerRelevanceModel != null
                ? answerRelevanceModel : DEFAULT_ANSWER_RELEVANCE_MODEL;
        final var mapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        final var registry = new MetricExecutorRegistry();

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

        @SuppressWarnings("unchecked") final var outputRelevanceClass = (Class<? extends Metric<?, ?>>) (Object) OutputRelevanceMetric.class;
        registry.register(outputRelevanceClass, new MetricExecutorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                                      ObjectMapper objectMapper,
                                                      ExecutorService executorService) {
                return (MetricExecutor<R, T>) new OutputRelevanceMetricExecutor<>(
                                                                                  (OutputRelevanceMetric<T>) metric,
                                                                                  judgeModel,
                                                                                  objectMapper,
                                                                                  executorService);
            }
        });

        return registry;
    }

    /**
     * Creates a registry pre-loaded with all built-in metric executors.
     *
     * @param objectMapper mapper used by JSON-dependent metric executors
     */
    public static MetricExecutorRegistry withDefaults(ObjectMapper objectMapper) {
        return withDefaults(null, objectMapper);
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
