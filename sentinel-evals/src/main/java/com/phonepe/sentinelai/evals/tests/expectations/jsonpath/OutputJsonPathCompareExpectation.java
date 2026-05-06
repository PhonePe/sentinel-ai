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

import lombok.ToString;

/**
 * Expectation definition that asserts a JSON-Path expression in the output satisfies
 * a given comparison operator against an expected value.
 *
 * Computation is performed by {@code OutputJsonPathCompareExpectationExecutor}.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
@ToString
public class OutputJsonPathCompareExpectation<R, T> implements Expectation<R, T> {

    private final String jsonPath;
    private final Operator operator;
    private final Object expectedValue;

    public OutputJsonPathCompareExpectation(String jsonPath,
                                            Operator operator,
                                            Object expectedValue) {
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

    public Object getExpectedValue() {
        return expectedValue;
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public Operator getOperator() {
        return operator;
    }
}
