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
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorFactory;

import java.util.concurrent.ExecutorService;

/**
 * @deprecated Use {@link ExpectationExecutorRegistry#withDefaults()} instead.
 */
@Deprecated
public class DefaultExpectationExecutorFactory implements ExpectationExecutorFactory {

    private final ExpectationExecutorRegistry delegate;

    /**
     * Creates the deprecated factory backed by the default expectation registry.
     */
    public DefaultExpectationExecutorFactory() {
        this.delegate = ExpectationExecutorRegistry.withDefaults(JsonUtils.createMapper());
    }

    /**
     * Creates the deprecated factory backed by the default expectation registry.
     *
     * @param metricExecutorFactory metric factory used for metric-backed expectations
     */
    public DefaultExpectationExecutorFactory(MetricExecutorFactory metricExecutorFactory) {
        this.delegate = ExpectationExecutorRegistry.withDefaults(metricExecutorFactory, JsonUtils.createMapper());
    }

    /**
     * Creates the deprecated factory backed by the default expectation registry.
     *
     * @param metricExecutorFactory metric factory used for metric-backed expectations
     * @param objectMapper          mapper used by built-in JSON-dependent expectations
     */
    public DefaultExpectationExecutorFactory(MetricExecutorFactory metricExecutorFactory,
                                             ObjectMapper objectMapper) {
        this.delegate = ExpectationExecutorRegistry.withDefaults(metricExecutorFactory, objectMapper);
    }

    /**
     * Creates the deprecated factory backed by the default expectation registry.
     *
     * @param objectMapper mapper used by built-in JSON-dependent expectations
     */
    public DefaultExpectationExecutorFactory(ObjectMapper objectMapper) {
        this.delegate = ExpectationExecutorRegistry.withDefaults(objectMapper);
    }

    /**
     * Delegates executor creation to the underlying registry.
     *
     * @param agent       agent being evaluated
     * @param expectation expectation definition to resolve
     * @param <R>         result/output type
     * @param <T>         input/request type
     * @param <A>         concrete agent type
     * @return matching expectation executor
     */
    @Override
    public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                             Expectation<R, T> expectation,
                                                                             ObjectMapper objectMapper,
                                                                             ExecutorService executorService) {
        return delegate.create(agent, expectation, objectMapper, executorService);
    }
}
