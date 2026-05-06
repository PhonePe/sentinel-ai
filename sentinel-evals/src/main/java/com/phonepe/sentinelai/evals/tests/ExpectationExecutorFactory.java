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

/**
 * Abstract factory that creates an {@link ExpectationExecutor} for a given {@link Expectation} definition.
 *
 * Implementations determine which executor corresponds to each expectation type, allowing
 * definitions (what to assert) and execution logic (how to evaluate) to evolve independently.
 */
public interface ExpectationExecutorFactory {

    /**
     * Create an executor capable of evaluating the supplied expectation.
     *
     * @param expectation the expectation definition
     * @param <R>         result/output type
     * @param <T>         input/request type
     * @return an {@link ExpectationExecutor} for the supplied expectation
     * @throws IllegalArgumentException if no executor is registered for the expectation type
     */
    <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(Agent<R, T, A> agent,
                                                                      Expectation<R, T> expectation);
}
