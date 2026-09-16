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

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.evals.tests.TestFactory;
import com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation;

import lombok.AllArgsConstructor;
import lombok.Value;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputJsonPathEqualsExpectationTest {

    @Value
    @AllArgsConstructor
    static class Decision {
        String status;
        String explanation;
    }

    @Test
    void testFailsWhenFieldValueDiffers() {
        final var expectation = new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>("testFailsWhenFieldValueDiffers",
                                                                                                                                "$.status",
                                                                                                                                Operator.EQ,
                                                                                                                                "FAILED");

        assertFalse(TestFactory.evaluate(expectation,
                                         new Decision("SUCCESS", "Everything is fine"),
                                         TestFactory.context()));
    }

    @Test
    void testFailsWhenPathMissing() {
        final var expectation = new OutputJsonPathCompareExpectation<>("testFailsWhenPathMissing",
                                                                       "$.nested.status",
                                                                       Operator.EQ,
                                                                       "SUCCESS");

        assertFalse(TestFactory.evaluate(expectation,
                                         new Decision("SUCCESS", "Everything is fine"),
                                         TestFactory.context()));
    }

    @Test
    void testMatchesFieldUsingJsonPath() {
        final var expectation = new com.phonepe.sentinelai.evals.tests.expectations.jsonpath.OutputJsonPathCompareExpectation<>("testMatchesFieldUsingJsonPath",
                                                                                                                                "$.status",
                                                                                                                                Operator.EQ,
                                                                                                                                "SUCCESS");

        assertTrue(TestFactory.evaluate(expectation,
                                        new Decision("SUCCESS", "Everything is fine"),
                                        TestFactory.context()));
    }
}
