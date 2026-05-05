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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.Expectation;

import lombok.ToString;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

@ToString
public class OutputJsonPathCompareExpectation<R, T> implements Expectation<R, T> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    @SuppressWarnings({
            "unchecked", "rawtypes"
    })
    private static int compareOrder(Object actual,
                                    Object expected) {
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

    @Override
    public boolean evaluate(R result,
                            EvalExpectationContext<T> context) {
        if (result == null) {
            return operator == Operator.EQ && expectedValue == null;
        }
        try {
            final var document = OBJECT_MAPPER.convertValue(result, Object.class);
            final var actualValue = JsonPath.read(document, jsonPath);
            return compare(actualValue);
        }
        catch (RuntimeException e) {
            return false;
        }
    }

    private boolean compare(Object actualValue) {
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
}
