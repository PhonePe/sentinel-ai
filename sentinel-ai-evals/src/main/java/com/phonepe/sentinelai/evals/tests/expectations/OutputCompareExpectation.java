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
 * Expectation that compares the entire output value against an expected value using an {@link Operator}.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public class OutputCompareExpectation<R, T> extends Expectation<R, T> {

    private final Object expectedValue;
    private final Operator operator;

    /**
     * Creates an output comparison expectation.
     *
     * @param id            unique identifier for this expectation
     * @param expectedValue expected value to compare against
     * @param operator      comparison operator to apply
     */
    public OutputCompareExpectation(String id, Object expectedValue, Operator operator) {
        super(id);
        this.expectedValue = expectedValue;
        this.operator = operator;
    }

    public Object getExpectedValue() {
        return expectedValue;
    }

    public Operator getOperator() {
        return operator;
    }
}
