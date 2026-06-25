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

package com.phonepe.sentinelai.evals.tests.expectations.jsonpath;

import com.phonepe.sentinelai.evals.tests.Expectation;
import com.phonepe.sentinelai.evals.tests.expectations.Operator;

/**
 * Expectation definition that asserts a JSON-Path expression in the output satisfies
 * a given comparison operator against an expected value.
 *
 * Computation is performed by {@code OutputJsonPathCompareExpectationExecutor}.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public class OutputJsonPathCompareExpectation<R, T> extends Expectation<R, T> {

    private final String jsonPath;
    private final Operator operator;
    private final Object expectedValue;

    /**
     * Creates a JSONPath comparison expectation.
     *
     * @param id            unique identifier for this expectation
     * @param jsonPath      JSONPath expression to evaluate against the output
     * @param operator      comparison operator to apply
     * @param expectedValue expected comparison value
     */
    public OutputJsonPathCompareExpectation(String id,
                                            String jsonPath,
                                            Operator operator,
                                            Object expectedValue) {
        super(id);
        this.jsonPath = normalizePath(jsonPath);
        this.operator = operator;
        this.expectedValue = expectedValue;
    }

    private static String normalizePath(String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) {
            throw new IllegalArgumentException("jsonPath must not be blank");
        }
        final var trimmed = jsonPath.trim();
        if (trimmed.startsWith("$")) {
            return trimmed;
        }
        return "$." + trimmed;
    }

    /**
     * Returns the expected comparison value.
     *
     * @return expected value used during comparison
     */
    public Object getExpectedValue() {
        return expectedValue;
    }

    /**
     * Returns the normalized JSONPath expression.
     *
     * @return JSONPath expression starting with {@code $}
     */
    public String getJsonPath() {
        return jsonPath;
    }

    /**
     * Returns the operator used for comparison.
     *
     * @return comparison operator
     */
    public Operator getOperator() {
        return operator;
    }
}
