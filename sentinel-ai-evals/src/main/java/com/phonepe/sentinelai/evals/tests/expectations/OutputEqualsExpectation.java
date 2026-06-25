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

package com.phonepe.sentinelai.evals.tests.expectations;

import com.phonepe.sentinelai.evals.tests.Expectation;

/**
 * Expectation definition that asserts the output equals an expected value.
 *
 * Computation is performed by {@code OutputEqualsExpectationExecutor}.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public class OutputEqualsExpectation<R, T> extends Expectation<R, T> {

    private final R expectedOutput;

    /**
     * Creates an equality expectation for the full output value.
     *
     * @param id             unique identifier for this expectation
     * @param expectedOutput expected output value
     */
    public OutputEqualsExpectation(String id, R expectedOutput) {
        super(id);
        this.expectedOutput = expectedOutput;
    }

    /**
     * Returns the expected output value.
     *
     * @return expected output
     */
    public R getExpectedOutput() {
        return expectedOutput;
    }
}
