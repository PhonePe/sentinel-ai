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

import com.jayway.jsonpath.JsonPath;

import com.phonepe.sentinelai.evals.SerDe;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.Operator;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation;

import java.math.BigDecimal;
import java.util.Collection;
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

    public OutputJsonPathCompareExpectationExecutor(OutputJsonPathCompareExpectation<R, T> expectation) {
        this.expectation = expectation;
    }

    private static boolean compare(Object actualValue, Operator operator, Object expectedValue) {
        return switch (operator) {
            case EQ -> Objects.equals(actualValue, expectedValue);
            case NE -> !Objects.equals(actualValue, expectedValue);
            case GT -> compareOrder(actualValue, expectedValue) > 0;
            case GTE -> compareOrder(actualValue, expectedValue) >= 0;
            case LT -> compareOrder(actualValue, expectedValue) < 0;
            case LTE -> compareOrder(actualValue, expectedValue) <= 0;
            case IN -> expectedValue instanceof Collection<?> collection && collection.contains(actualValue);
            case NOT_IN -> expectedValue instanceof Collection<?> collection && !collection.contains(actualValue);
        };
    }

    @SuppressWarnings({
            "unchecked", "rawtypes"
    })
    private static int compareOrder(Object actual, Object expected) {
        if (actual == null || expected == null) {
            throw new IllegalArgumentException("Both actual and expected must be non-null for ordered comparisons");
        }
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return new BigDecimal(actualNumber.toString()).compareTo(new BigDecimal(expectedNumber.toString()));
        }
        if (actual.getClass().equals(expected.getClass()) && actual instanceof Comparable actualComparable) {
            return actualComparable.compareTo(expected);
        }
        throw new IllegalArgumentException("Unsupported ordered comparison between values");
    }

    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        if (result == null) {
            return expectation.getOperator() == Operator.EQ && expectation.getExpectedValue() == null;
        }
        try {
            final var document = SerDe.mapper().convertValue(result, Object.class);
            final var actualValue = JsonPath.read(document, expectation.getJsonPath());
            return compare(actualValue, expectation.getOperator(), expectation.getExpectedValue());
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
