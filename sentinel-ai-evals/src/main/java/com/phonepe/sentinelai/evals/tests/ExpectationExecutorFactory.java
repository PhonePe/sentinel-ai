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

import java.util.concurrent.ExecutorService;

/**
 * Abstract factory that creates an {@link ExpectationExecutor} for a given {@link Expectation} definition.
 *
 * Implementations determine which executor corresponds to each expectation type, allowing
 * definitions (what to assert) and execution logic (how to evaluate) to evolve independently.
 */
@FunctionalInterface
public interface ExpectationExecutorFactory {

    /**
     * Create an executor capable of evaluating the supplied expectation.
     *
     * @param agent           agent being evaluated; may be used to resolve tool metadata
     * @param expectation     the expectation definition
     * @param objectMapper    mapper supplied by the eval engine for JSON operations
     * @param executorService executor supplied by the eval engine for async work
     * @param <R>             result/output type
     * @param <T>             input/request type
     * @param <A>             concrete agent type
     * @return an {@link ExpectationExecutor} for the supplied expectation
     * @throws IllegalArgumentException if no executor is registered for the expectation type
     */
    <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                      Expectation<R, T> expectation,
                                                                      ObjectMapper objectMapper,
                                                                      ExecutorService executorService);
}
