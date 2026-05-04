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

import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.Expectations;

import lombok.Value;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputJsonPathCompareExpectationTest {

    @Value
    static class Decision {
        String status;
        int score;
        String region;
    }

    private static EvalExpectationContext<Object> emptyContext() {
        return new EvalExpectationContext<>("run",
                                            null,
                                            List.of(),
                                            new ModelUsageStats());
    }

    @Test
    void testGreaterThan() {
        final var expectation = Expectations.<Decision, Object>where("score").gt(80);

        assertTrue(expectation.evaluate(new Decision("SUCCESS", 91, "IN"), emptyContext()));
        assertFalse(expectation.evaluate(new Decision("SUCCESS", 50, "IN"), emptyContext()));
    }

    @Test
    void testInList() {
        final var expectation = Expectations.<Decision, Object>where("region").in(List.of("IN", "US", "SG"));

        assertTrue(expectation.evaluate(new Decision("SUCCESS", 91, "IN"), emptyContext()));
        assertFalse(expectation.evaluate(new Decision("SUCCESS", 91, "DE"), emptyContext()));
    }

    @Test
    void testLessThan() {
        final var expectation = Expectations.<Decision, Object>where("score").lt(30);

        assertTrue(expectation.evaluate(new Decision("SUCCESS", 20, "IN"), emptyContext()));
        assertFalse(expectation.evaluate(new Decision("SUCCESS", 31, "IN"), emptyContext()));
    }

    @Test
    void testNotEquals() {
        final var expectation = Expectations.<Decision, Object>at("status").ne("FAILED");

        assertTrue(expectation.evaluate(new Decision("SUCCESS", 91, "IN"), emptyContext()));
        assertFalse(expectation.evaluate(new Decision("FAILED", 91, "IN"), emptyContext()));
    }
}
