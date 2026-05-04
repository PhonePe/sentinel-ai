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

import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.Operator;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation;
import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;

import lombok.Value;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputJsonPathEqualsExpectationTest {

    @Value
    static class Decision {
        String status;
        String explanation;
    }

    private static EvalExpectationContext<Object> emptyContext() {
        return new EvalExpectationContext<>("run",
                                            null,
                                            List.of(),
                                            new ModelUsageStats());
    }

    @Test
    void testFailsWhenFieldValueDiffers() {
        final var expectation = new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>("$.status",
                                                                       Operator.EQ,
                                                                       "FAILED");

        assertFalse(expectation.evaluate(
                                         new Decision("SUCCESS", "Everything is fine"),
                                         emptyContext()));
    }

    @Test
    void testFailsWhenPathMissing() {
        final var expectation = new OutputJsonPathCompareExpectation<>("$.nested.status",
                                                                       Operator.EQ,
                                                                       "SUCCESS");

        assertFalse(expectation.evaluate(
                                         new Decision("SUCCESS", "Everything is fine"),
                                         emptyContext()));
    }

    @Test
    void testMatchesFieldUsingJsonPath() {
        final var expectation = new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>("$.status",
                                                                       Operator.EQ,
                                                                       "SUCCESS");

        assertTrue(expectation.evaluate(
                                        new Decision("SUCCESS", "Everything is fine"),
                                        emptyContext()));
    }
}
