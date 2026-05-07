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

package com.phonepe.sentinelai.evals.tests;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.evals.tests.expectations.OrderedExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OutputContainsExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OutputEqualsExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.ToolCalledExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.executors.MessageExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.MetricExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.OrderedExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.OutputContainsExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.OutputEqualsExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.ToolCalledExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.jsonpath.OutputJsonPathCompareExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorFactory;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExpectation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * Registry-based {@link ExpectationExecutorFactory} that dispatches to a registered
 * {@link ExpectationExecutorFactory} per {@link Expectation} class.
 *
 * <p>Library users can extend the built-in set by calling {@link #register}:
 *
 * <pre>{@code
 * ExpectationExecutorRegistry registry = ExpectationExecutorRegistry.withDefaults()
 *     .register(MyExpectation.class, new ExpectationExecutorFactory() {
 *         public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
 *                                                                                   Expectation<R, T> expectation,
 *                                                                                   ObjectMapper objectMapper,
 *                                                                                   ExecutorService executorService) {
 *             return new MyExpectationExecutor((MyExpectation<R, T>) expectation);
 * }
 * });
 * }</pre>
 */
public class ExpectationExecutorRegistry implements ExpectationExecutorFactory {

    private final Map<Class<?>, ExpectationExecutorFactory> registry = new LinkedHashMap<>();

    /**
     * Creates an empty expectation registry backed by the supplied metric factory.
     *
     * @param metricExecutorFactory metric factory used when registering metric-backed expectations
     */
    public ExpectationExecutorRegistry(MetricExecutorFactory metricExecutorFactory) {
        Objects.requireNonNull(metricExecutorFactory, "metricExecutorFactory cannot be null");
    }

    /**
     * Creates a registry pre-loaded with all built-in expectation executors.
     */
    public static ExpectationExecutorRegistry withDefaults() {
        return withDefaults(null, JsonUtils.createMapper());
    }

    /**
     * Creates a registry pre-loaded with all built-in expectation executors.
     *
     * @param metricExecutorFactory factory for evaluating metric expectations;
     *                              {@code null} falls back to the built-in defaults
     */
    public static ExpectationExecutorRegistry withDefaults(MetricExecutorFactory metricExecutorFactory) {
        return withDefaults(metricExecutorFactory, JsonUtils.createMapper());
    }

    /**
     * Creates a registry pre-loaded with all built-in expectation executors.
     *
     * @param metricExecutorFactory factory for evaluating metric expectations;
     *                              {@code null} falls back to the built-in defaults
     * @param objectMapper          mapper used by built-in JSON-dependent executors
     */
    public static ExpectationExecutorRegistry withDefaults(MetricExecutorFactory metricExecutorFactory,
                                                           ObjectMapper objectMapper) {
        final var mapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        final var metricsFactory = metricExecutorFactory != null
                ? metricExecutorFactory : MetricExecutorRegistry.withDefaults(mapper);
        final var registry = new ExpectationExecutorRegistry(metricsFactory);

        @SuppressWarnings("unchecked") final var outputEqualsClass = (Class<? extends Expectation<?, ?>>) (Object) OutputEqualsExpectation.class;
        registry.register(outputEqualsClass, new ExpectationExecutorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                return (ExpectationExecutor<R, T>) new OutputEqualsExpectationExecutor<>(
                                                                                         (OutputEqualsExpectation<R, T>) expectation);
            }
        });

        @SuppressWarnings("unchecked") final var outputContainsClass = (Class<? extends Expectation<?, ?>>) (Object) OutputContainsExpectation.class;
        registry.register(outputContainsClass, new ExpectationExecutorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                return (ExpectationExecutor<R, T>) new OutputContainsExpectationExecutor<>(
                                                                                           (OutputContainsExpectation<T>) expectation);
            }
        });

        @SuppressWarnings("unchecked") final var toolCalledClass = (Class<? extends Expectation<?, ?>>) (Object) ToolCalledExpectation.class;
        registry.register(toolCalledClass, new ExpectationExecutorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                return (ExpectationExecutor<R, T>) new ToolCalledExpectationExecutor<>(
                                                                                       (ToolCalledExpectation<R, T>) expectation,
                                                                                       agent,
                                                                                       objectMapper);
            }
        });

        @SuppressWarnings("unchecked") final var orderedClass = (Class<? extends Expectation<?, ?>>) (Object) OrderedExpectation.class;
        registry.register(orderedClass, new ExpectationExecutorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                final var orderedExp = (OrderedExpectation<R, T>) expectation;
                final var messageExpectationExecutors = orderedExp.getExpectations()
                        .stream()
                        .map(me -> (ExpectationExecutor<Object, Object>) registry.create(agent,
                                                                                         me,
                                                                                         objectMapper,
                                                                                         executorService))
                        .filter(ex -> ex instanceof MessageExpectationExecutor<?, ?, ?>)
                        .map(ex -> (MessageExpectationExecutor<?, R, T>) (Object) ex)
                        .toList();
                return (ExpectationExecutor<R, T>) new OrderedExpectationExecutor<>(orderedExp,
                                                                                    (List<MessageExpectationExecutor<?, R, T>>) (Object) messageExpectationExecutors);
            }
        });

        @SuppressWarnings("unchecked") final var metricExpectationClass = (Class<? extends Expectation<?, ?>>) (Object) MetricExpectation.class;
        registry.register(metricExpectationClass, new ExpectationExecutorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                return (ExpectationExecutor<R, T>) new MetricExpectationExecutor<>(
                                                                                   (MetricExpectation<R, T>) expectation,
                                                                                   metricsFactory,
                                                                                   objectMapper,
                                                                                   executorService);
            }
        });

        @SuppressWarnings("unchecked") final var jsonPathClass = (Class<? extends Expectation<?, ?>>) (Object) OutputJsonPathCompareExpectation.class;
        registry.register(jsonPathClass, new ExpectationExecutorFactory() {
            @Override
            @SuppressWarnings("unchecked")
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                return (ExpectationExecutor<R, T>) new OutputJsonPathCompareExpectationExecutor<>(
                                                                                                  (OutputJsonPathCompareExpectation<R, T>) expectation,
                                                                                                  objectMapper);
            }
        });

        return registry;
    }

    /**
     * Creates a registry pre-loaded with all built-in expectation executors.
     *
     * @param objectMapper mapper used by built-in JSON-dependent executors
     */
    public static ExpectationExecutorRegistry withDefaults(ObjectMapper objectMapper) {
        return withDefaults(null, objectMapper);
    }

    /**
     * Resolves an executor for the supplied expectation instance.
     *
     * @param agent       agent being evaluated
     * @param expectation expectation definition to resolve
     * @param <R>         result/output type
     * @param <T>         input/request type
     * @param <A>         concrete agent type
     * @return executor registered for the expectation class
     */
    @Override
    public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                             Expectation<R, T> expectation,
                                                                             ObjectMapper objectMapper,
                                                                             ExecutorService executorService) {
        final var factory = registry.get(expectation.getClass());
        if (factory == null) {
            throw new IllegalArgumentException(
                                               "No ExpectationExecutor registered for expectation type: " + expectation
                                                       .getClass().getName()
                                                       + ". Register it via ExpectationExecutorRegistry.register().");
        }
        return factory.create(agent, expectation, objectMapper, executorService);
    }

    /**
     * Registers an {@link ExpectationExecutorFactory} for a specific {@link Expectation} class.
     * Overwrites any previously registered factory for the same class.
     *
     * @param expectationClass the concrete expectation class to handle
     * @param factory          factory that creates executors for this expectation type
     * @return this registry (fluent)
     */
    public ExpectationExecutorRegistry register(Class<? extends Expectation<?, ?>> expectationClass,
                                                ExpectationExecutorFactory factory) {
        Objects.requireNonNull(expectationClass, "expectationClass cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        registry.put(expectationClass, factory);
        return this;
    }
}
