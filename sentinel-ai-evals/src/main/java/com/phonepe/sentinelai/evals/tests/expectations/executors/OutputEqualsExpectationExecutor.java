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

package com.phonepe.sentinelai.evals.tests.expectations.executors;

import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.OutputEqualsExpectation;

import java.util.Objects;

/**
 * Executor for {@link OutputEqualsExpectation}.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public class OutputEqualsExpectationExecutor<R, T> implements ExpectationExecutor<R, T> {

    private final OutputEqualsExpectation<R, T> expectation;

    /**
     * Creates an executor for equality-based output expectations.
     *
     * @param expectation expectation definition to evaluate
     */
    public OutputEqualsExpectationExecutor(OutputEqualsExpectation<R, T> expectation) {
        this.expectation = expectation;
    }

    /**
     * Checks whether the output equals the configured expected value.
     *
     * @param result  output to compare
     * @param context evaluation context (unused)
     * @return {@code true} when the values are equal
     */
    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        return Objects.equals(result, expectation.getExpectedOutput());
    }

    /**
     * Returns the textual representation of the underlying expectation.
     *
     * @return expectation description
     */
    @Override
    public String toString() {
        return expectation.toString();
    }
}
