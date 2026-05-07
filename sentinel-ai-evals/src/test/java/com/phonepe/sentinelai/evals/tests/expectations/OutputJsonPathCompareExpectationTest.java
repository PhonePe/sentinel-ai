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

import com.phonepe.sentinelai.evals.tests.Expectations;
import com.phonepe.sentinelai.evals.tests.TestFactory;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputJsonPathCompareExpectationTest {

    @Data
    @AllArgsConstructor
    static class Decision {
        String status;
        int score;
        String region;
    }

    @Test
    void testEquals() {
        final var expectation = Expectations.<Decision, Object>at("status").eq("SUCCESS");

        assertTrue(TestFactory.evaluate(expectation, new Decision("SUCCESS", 91, "IN"), TestFactory.context()));
        assertFalse(TestFactory.evaluate(expectation, new Decision("FAILED", 91, "IN"), TestFactory.context()));
    }

    @Test
    void testGreaterThan() {
        final var expectation = Expectations.<Decision, Object>where("score").gt(80);

        assertTrue(TestFactory.evaluate(expectation, new Decision("SUCCESS", 91, "IN"), TestFactory.context()));
        assertFalse(TestFactory.evaluate(expectation, new Decision("SUCCESS", 50, "IN"), TestFactory.context()));
    }

    @Test
    void testGreaterThanOrEquals() {
        final var expectation = Expectations.<Decision, Object>where("score").gte(80);

        assertTrue(TestFactory.evaluate(expectation, new Decision("SUCCESS", 80, "IN"), TestFactory.context()));
        assertFalse(TestFactory.evaluate(expectation, new Decision("SUCCESS", 79, "IN"), TestFactory.context()));
    }

    @Test
    void testInList() {
        final var expectation = Expectations.<Decision, Object>where("region").in(List.of("IN", "US", "SG"));

        assertTrue(TestFactory.evaluate(expectation, new Decision("SUCCESS", 91, "IN"), TestFactory.context()));
        assertFalse(TestFactory.evaluate(expectation, new Decision("SUCCESS", 91, "DE"), TestFactory.context()));
    }

    @Test
    void testLessThan() {
        final var expectation = Expectations.<Decision, Object>where("score").lt(30);

        assertTrue(TestFactory.evaluate(expectation, new Decision("SUCCESS", 20, "IN"), TestFactory.context()));
        assertFalse(TestFactory.evaluate(expectation, new Decision("SUCCESS", 31, "IN"), TestFactory.context()));
    }

    @Test
    void testLessThanOrEquals() {
        final var expectation = Expectations.<Decision, Object>where("score").lte(30);

        assertTrue(TestFactory.evaluate(expectation, new Decision("SUCCESS", 30, "IN"), TestFactory.context()));
        assertFalse(TestFactory.evaluate(expectation, new Decision("SUCCESS", 31, "IN"), TestFactory.context()));
    }

    @Test
    void testNotEquals() {
        final var expectation = Expectations.<Decision, Object>at("status").ne("FAILED");

        assertTrue(TestFactory.evaluate(expectation, new Decision("SUCCESS", 91, "IN"), TestFactory.context()));
        assertFalse(TestFactory.evaluate(expectation, new Decision("FAILED", 91, "IN"), TestFactory.context()));
    }

    @Test
    void testNotInList() {
        final var expectation = Expectations.<Decision, Object>where("region").notIn(List.of("DE", "FR"));

        assertTrue(TestFactory.evaluate(expectation, new Decision("SUCCESS", 91, "IN"), TestFactory.context()));
        assertFalse(TestFactory.evaluate(expectation, new Decision("SUCCESS", 91, "DE"), TestFactory.context()));
    }
}
