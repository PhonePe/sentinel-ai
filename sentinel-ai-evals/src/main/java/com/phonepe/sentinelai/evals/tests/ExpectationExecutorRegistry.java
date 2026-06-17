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
import com.phonepe.sentinelai.evals.AgentEventTracer;
import com.phonepe.sentinelai.evals.tests.expectations.AgentEventExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OrderedExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OutputCompareExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OutputContainsExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.OutputEqualsExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.ToolCalledExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.executors.AgentEventExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.MessageExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.MetricExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.OrderedExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.OutputCompareExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.OutputContainsExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.OutputEqualsExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.ToolCalledExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.executors.jsonpath.OutputJsonPathCompareExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation;
import com.phonepe.sentinelai.evals.tests.metrics.EmbeddingModelFactory;
import com.phonepe.sentinelai.evals.tests.metrics.LLMModelFactory;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorFactory;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExpectation;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<Class<?>, ExpectationExecutorFactory> registry = new ConcurrentHashMap<>();


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
                ? metricExecutorFactory : MetricExecutorRegistry.withDefaults(null,
                                                                              EmbeddingModelFactory.noOp(),
                                                                              null,
                                                                              LLMModelFactory.noOp(),
                                                                              mapper);

        final var registry = new ExpectationExecutorRegistry();

        registry.registerExpectation(OutputEqualsExpectation.class, new ExpectationExecutorFactory() {
            @Override
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                final var typedExpectation = (OutputEqualsExpectation<R, T>) expectation;
                return new OutputEqualsExpectationExecutor<>(typedExpectation);
            }
        });

        registry.registerExpectation(OutputContainsExpectation.class, new ExpectationExecutorFactory() {
            @Override
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                final var typedExpectation = (OutputContainsExpectation<T>) expectation;
                return (ExpectationExecutor<R, T>) new OutputContainsExpectationExecutor<>(typedExpectation);
            }
        });

        registry.registerExpectation(ToolCalledExpectation.class, new ExpectationExecutorFactory() {
            @Override
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                final var typedExpectation = (ToolCalledExpectation<R, T>) expectation;
                return new ToolCalledExpectationExecutor<>(typedExpectation, agent, objectMapper);
            }
        });

        registry.registerExpectation(OrderedExpectation.class, new ExpectationExecutorFactory() {
            @Override
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                final var orderedExp = (OrderedExpectation<R, T>) expectation;
                final var messageExpectationExecutors = registry.resolveMessageExecutors(
                                                                                         agent,
                                                                                         orderedExp.getExpectations(),
                                                                                         objectMapper,
                                                                                         executorService);
                return new OrderedExpectationExecutor<>(orderedExp, messageExpectationExecutors);
            }
        });

        registry.registerExpectation(MetricExpectation.class, new ExpectationExecutorFactory() {
            @Override
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                final var typedExpectation = (MetricExpectation<R, T>) expectation;
                return new MetricExpectationExecutor<>(typedExpectation,
                                                       metricsFactory,
                                                       objectMapper,
                                                       executorService);
            }
        });

        registry.registerExpectation(OutputCompareExpectation.class, new ExpectationExecutorFactory() {
            @Override
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                final var typedExpectation = (OutputCompareExpectation<R, T>) expectation;
                return new OutputCompareExpectationExecutor<>(typedExpectation);
            }
        });

        registry.registerExpectation(OutputJsonPathCompareExpectation.class, new ExpectationExecutorFactory() {
            @Override
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                final var typedExpectation = (OutputJsonPathCompareExpectation<R, T>) expectation;
                return new OutputJsonPathCompareExpectationExecutor<>(typedExpectation, objectMapper);
            }
        });

        registry.withEventExpectations(new AgentEventTracer());

        return registry;
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
        Class<?> clazz = expectation.getClass();
        while (clazz != null && clazz != Object.class) {
            final var factory = registry.get(clazz);
            if (factory != null) {
                return factory.create(agent, expectation, objectMapper, executorService);
            }
            clazz = clazz.getSuperclass();
        }
        throw new IllegalArgumentException(
                                           "No ExpectationExecutor registered for expectation type: " + expectation
                                                   .getClass().getName()
                                                   + ". Register it via ExpectationExecutorRegistry.register().");
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

    public ExpectationExecutorRegistry withEventExpectations(AgentEventTracer tracer) {
        register((Class) AgentEventExpectation.class, new ExpectationExecutorFactory() {
            @Override
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(
                                                                                     Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     ExecutorService executorService) {
                return (ExpectationExecutor<R, T>) new AgentEventExpectationExecutor<>(
                                                                                       (AgentEventExpectation<R, T>) expectation,
                                                                                       tracer,
                                                                                       objectMapper);
            }
        });
        return this;
    }

    private <E extends Expectation<?, ?>> void registerExpectation(Class<E> expectationClass,
                                                                   ExpectationExecutorFactory factory) {
        Objects.requireNonNull(expectationClass, "expectationClass cannot be null");
        Objects.requireNonNull(factory, "factory cannot be null");
        register((Class<? extends Expectation<?, ?>>) (Object) expectationClass, factory);
    }

    @SuppressWarnings("unchecked")
    private <R, T, A extends Agent<R, T, A>> List<MessageExpectationExecutor<?, R, T>> resolveMessageExecutors(
                                                                                                               Agent<R, T, A> agent,
                                                                                                               List<MessageExpectation<R, T>> expectations,
                                                                                                               ObjectMapper objectMapper,
                                                                                                               ExecutorService executorService) {
        final List<MessageExpectationExecutor<?, R, T>> result = new java.util.ArrayList<>();
        for (MessageExpectation<R, T> expectation : expectations) {
            final var executor = create(agent, expectation, objectMapper, executorService);
            if (executor instanceof MessageExpectationExecutor<?, ?, ?> msgExec) {
                result.add((MessageExpectationExecutor<?, R, T>) msgExec);
            }
        }
        return result;
    }
}
