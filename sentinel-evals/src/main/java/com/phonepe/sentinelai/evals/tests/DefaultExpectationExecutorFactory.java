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

import com.phonepe.sentinelai.core.agent.Agent;
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
import com.phonepe.sentinelai.evals.tests.metrics.DefaultMetricExecutorFactory;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorFactory;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExpectation;

import java.util.stream.Collectors;

/**
 * Default concrete implementation of {@link ExpectationExecutorFactory}.
 */
public class DefaultExpectationExecutorFactory implements ExpectationExecutorFactory {

    private final MetricExecutorFactory metricExecutorFactory;

    public DefaultExpectationExecutorFactory() {
        this(new DefaultMetricExecutorFactory());
    }

    public DefaultExpectationExecutorFactory(MetricExecutorFactory metricExecutorFactory) {
        this.metricExecutorFactory = metricExecutorFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                             Expectation<R, T> expectation) {
        if (expectation instanceof OutputContainsExpectation<?> e) {
            return (ExpectationExecutor<R, T>) new OutputContainsExpectationExecutor<>((OutputContainsExpectation<T>) e);
        }
        if (expectation instanceof OutputEqualsExpectation<?, ?> e) {
            return (ExpectationExecutor<R, T>) new OutputEqualsExpectationExecutor<>((OutputEqualsExpectation<R, T>) e);
        }
        if (expectation instanceof ToolCalledExpectation<?, ?> e) {
            return (ExpectationExecutor<R, T>) new ToolCalledExpectationExecutor<>((ToolCalledExpectation<R, T>) e,
                                                                                   agent);
        }
        if (expectation instanceof OrderedExpectation<?, ?> e) {
            @SuppressWarnings("unchecked") final var subExecutors = ((OrderedExpectation<R, T>) e).getExpectations()
                    .stream()
                    .<MessageExpectationExecutor<?, R, T>>map(me -> (MessageExpectationExecutor<?, R, T>) create(agent,
                                                                                                                 me))
                    .collect(Collectors.toList());
            return (ExpectationExecutor<R, T>) new OrderedExpectationExecutor<>((OrderedExpectation<R, T>) e,
                                                                                subExecutors);
        }
        if (expectation instanceof OutputJsonPathCompareExpectation<?, ?> e) {
            return (ExpectationExecutor<R, T>) new OutputJsonPathCompareExpectationExecutor<>(
                                                                                              (OutputJsonPathCompareExpectation<R, T>) e);
        }
        if (expectation instanceof MetricExpectation<?, ?> e) {
            return (ExpectationExecutor<R, T>) new MetricExpectationExecutor<>((MetricExpectation<R, T>) e,
                                                                               metricExecutorFactory);
        }
        throw new IllegalArgumentException(
                                           "No ExpectationExecutor registered for expectation type: " + expectation
                                                   .getClass().getName());
    }
}
