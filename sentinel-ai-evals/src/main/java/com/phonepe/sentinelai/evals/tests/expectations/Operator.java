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

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Objects;

/**
 * Comparison operators used by {@link com.phonepe.sentinelai.evals.tests.expectations.OutputCompareExpectation}
 * and {@link com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation}.
 */
public enum Operator {
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    IN,
    NOT_IN,
    CONTAINS,
    NOT_CONTAINS;

    private static <T extends Comparable<? super T>> int castAndCompare(Comparable<?> actual, Object expected) {
        return ((T) actual).compareTo((T) expected);
    }

    private static boolean compareContains(Object actual, Object expected) {
        if (actual instanceof String actualStr && expected instanceof String expectedStr) {
            return actualStr.contains(expectedStr);
        }
        throw new IllegalArgumentException("CONTAINS requires both values to be Strings");
    }

    private static int compareOrder(Object actual, Object expected) {
        if (actual == null || expected == null) {
            throw new IllegalArgumentException("Both actual and expected must be non-null for ordered comparisons");
        }
        if (actual instanceof Number actualNumber && expected instanceof Number expectedNumber) {
            return new BigDecimal(actualNumber.toString()).compareTo(new BigDecimal(expectedNumber.toString()));
        }
        if (actual.getClass().equals(expected.getClass()) && actual instanceof Comparable<?> actualComparable) {
            return castAndCompare(actualComparable, expected);
        }
        throw new IllegalArgumentException("Unsupported ordered comparison between " + actual.getClass()
                + " and " + expected.getClass());
    }

    /**
     * Compares two values using this operator.
     *
     * @param actual   value extracted from the output or event
     * @param expected value supplied in the expectation definition
     * @return {@code true} if the comparison holds
     */
    public boolean compare(Object actual, Object expected) {
        return switch (this) {
            case EQ -> Objects.equals(actual, expected);
            case NE -> !Objects.equals(actual, expected);
            case GT -> compareOrder(actual, expected) > 0;
            case GTE -> compareOrder(actual, expected) >= 0;
            case LT -> compareOrder(actual, expected) < 0;
            case LTE -> compareOrder(actual, expected) <= 0;
            case IN -> expected instanceof Collection<?> collection && collection.contains(actual);
            case NOT_IN -> expected instanceof Collection<?> collection && !collection.contains(actual);
            case CONTAINS -> compareContains(actual, expected);
            case NOT_CONTAINS -> !compareContains(actual, expected);
        };
    }
}
