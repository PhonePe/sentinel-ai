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

import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.evals.tests.TestFactory;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolCalledExpectationTest {

    @Test
    void testFailsWhenParamsDoNotMatch() {
        final var expectation = new ToolCalledExpectation<>("get_weather", 1, Map.of("city", "Mumbai"));
        final var context = TestFactory.contextWith(List.of(
                                                            new ToolCall("session",
                                                                         "run",
                                                                         "tc-1",
                                                                         "get_weather",
                                                                         "{\"city\":\"Bengaluru\"}")));

        assertFalse(TestFactory.evaluate(expectation, null, context));
    }

    @Test
    void testFailsWhenTimesDoNotMatchExactly() {
        final var expectation = new ToolCalledExpectation<>("get_weather", 1, null);
        final var context = TestFactory.contextWith(List.of(
                                                            new ToolCall("session",
                                                                         "run",
                                                                         "tc-1",
                                                                         "get_weather",
                                                                         "{\"city\":\"Bengaluru\"}"),
                                                            new ToolCall("session",
                                                                         "run",
                                                                         "tc-2",
                                                                         "get_weather",
                                                                         "{\"city\":\"Pune\"}")));

        assertFalse(TestFactory.evaluate(expectation, null, context));
    }

    @Test
    void testMatchesExactTimesAndParams() {
        final var expectation = new ToolCalledExpectation<>("get_weather", 2, Map.of("city", "Bengaluru"));
        final var context = TestFactory.contextWith(List.of(
                                                            new ToolCall("session",
                                                                         "run",
                                                                         "tc-1",
                                                                         "get_weather",
                                                                         "{\"city\":\"Bengaluru\"}"),
                                                            new ToolCall("session",
                                                                         "run",
                                                                         "tc-2",
                                                                         "get_weather",
                                                                         "{\"city\":\"Bengaluru\"}"),
                                                            new ToolCall("session",
                                                                         "run",
                                                                         "tc-3",
                                                                         "get_city",
                                                                         "{\"country\":\"IN\"}")));

        assertTrue(TestFactory.evaluate(expectation, null, context));
    }

    @Test
    void testRejectsInvalidTimes() {
        final var expectation = new ToolCalledExpectation<>("get_weather", 0, null);
        final var context = TestFactory.contextWith(List.of(
                                                            new ToolCall("session",
                                                                         "run",
                                                                         "tc-1",
                                                                         "get_weather",
                                                                         "{\"city\":\"Bengaluru\"}")));

        assertThrows(IllegalArgumentException.class, () -> TestFactory.evaluate(expectation, null, context));
    }
}
