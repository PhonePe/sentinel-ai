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

package com.phonepe.sentinelai.evals.tests.expectations.executors.jsonpath;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation;

import java.util.Objects;

/**
 * Executor for {@link OutputJsonPathCompareExpectation} – evaluates a JSON-Path expression
 * against the serialised output and compares the extracted value using the configured operator.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public class OutputJsonPathCompareExpectationExecutor<R, T> implements ExpectationExecutor<R, T> {

    private final OutputJsonPathCompareExpectation<R, T> expectation;
    private final ObjectMapper objectMapper;

    /**
     * Creates an executor for JSONPath comparison expectations.
     *
     * @param expectation  expectation definition to evaluate
     * @param objectMapper mapper used to convert output to JSON-compatible document
     */
    public OutputJsonPathCompareExpectationExecutor(OutputJsonPathCompareExpectation<R, T> expectation,
                                                    ObjectMapper objectMapper) {
        this.expectation = expectation;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        if (result == null) {
            return expectation.getOperator().compare(null, expectation.getExpectedValue());
        }
        try {
            final var document = objectMapper.convertValue(result, Object.class);
            final var actualValue = JsonPath.read(document, expectation.getJsonPath());
            return expectation.getOperator().compare(actualValue, expectation.getExpectedValue());
        }
        catch (RuntimeException e) {
            return false;
        }
    }

    @Override
    public String toString() {
        return expectation.toString();
    }
}
