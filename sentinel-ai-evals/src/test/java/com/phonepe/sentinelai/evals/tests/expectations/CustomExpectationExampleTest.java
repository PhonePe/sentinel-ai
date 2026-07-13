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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.evals.EvalEngine;
import com.phonepe.sentinelai.evals.EvalStatus;
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.Expectation;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutorFactory;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutorRegistry;
import com.phonepe.sentinelai.evals.tests.TestCase;
import com.phonepe.sentinelai.evals.tests.TestFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Example test demonstrating how to create custom expectations.
 *
 * This example shows:
 * 1. Creating a custom Expectation class (StringLengthExpectation)
 * 2. Creating a custom ExpectationExecutor (StringLengthExpectationExecutor)
 * 3. Registering the custom expectation with ExpectationExecutorRegistry
 * 4. Using the custom expectation in tests
 */
class CustomExpectationExampleTest {

    /**
     * BONUS: Another Custom Expectation Example - Demonstrates flexibility
     *
     * This shows how easy it is to create additional custom expectations
     * for your specific domain needs.
     */
    static class ResponseTimeExpectation extends Expectation<String, String> {
        private final long maxMillis;

        public ResponseTimeExpectation(long maxMillis) {
            super("response-time");
            this.maxMillis = maxMillis;
        }

        public long getMaxMillis() {
            return maxMillis;
        }
    }

    static class ResponseTimeExpectationExecutor implements ExpectationExecutor<String, String> {
        private final ResponseTimeExpectation expectation;

        public ResponseTimeExpectationExecutor(ResponseTimeExpectation expectation) {
            this.expectation = expectation;
        }

        @Override
        public boolean evaluate(String result, EvalExpectationContext<String> context) {
            // In a real scenario, you'd extract timing data from context
            // For this example, we use ModelUsageStats as a dummy value
            final long responseTime = context.getModelUsageStats().getTotalTokens(); // Dummy value
            return responseTime <= expectation.getMaxMillis();
        }
    }

    /**
     * STEP 1: Define a Custom Expectation Class
     *
     * An expectation is a pure data class that carries configuration describing
     * WHAT to assert. It should implement the Expectation interface and hold
     * all necessary parameters for evaluation.
     */
    static class StringLengthExpectation extends Expectation<String, String> {
        private final int minLength;
        private final int maxLength;

        public StringLengthExpectation(int minLength, int maxLength) {
            super("string-length");
            if (minLength < 0 || maxLength < minLength) {
                throw new IllegalArgumentException(
                                                   "minLength must be >= 0 and <= maxLength");
            }
            this.minLength = minLength;
            this.maxLength = maxLength;
        }

        public int getMaxLength() {
            return maxLength;
        }

        public int getMinLength() {
            return minLength;
        }
    }

    /**
     * STEP 2: Define a Custom Expectation Executor
     *
     * An executor performs the actual computation. It encapsulates all the
     * evaluation logic, keeping the expectation class as a pure data definition.
     */
    static class StringLengthExpectationExecutor implements ExpectationExecutor<String, String> {
        private final StringLengthExpectation expectation;

        public StringLengthExpectationExecutor(StringLengthExpectation expectation) {
            this.expectation = expectation;
        }

        @Override
        public boolean evaluate(String result, EvalExpectationContext<String> context) {
            if (result == null) {
                return false;
            }
            final int length = result.length();
            return length >= expectation.getMinLength() && length <= expectation.getMaxLength();
        }
    }

    @Test
    void demonstrateMultipleCustomExpectations() {
        final var stringLengthExp = new StringLengthExpectation(5, 100);
        final var responseTimeExp = new ResponseTimeExpectation(1000);

        // Create a registry with multiple custom expectations
        final var registry = ExpectationExecutorRegistry.withDefaults()
                .register(StringLengthExpectation.class,
                          new ExpectationExecutorFactory() {
                              @Override
                              @SuppressWarnings("unchecked")
                              public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(
                                                                                                       Agent<R, T, A> agent,
                                                                                                       Expectation<R, T> expectation,
                                                                                                       ObjectMapper objectMapper,
                                                                                                       ExecutorService executorService) {
                                  return (ExpectationExecutor<R, T>) new StringLengthExpectationExecutor(
                                                                                                         (StringLengthExpectation) expectation);
                              }
                          })
                .register(ResponseTimeExpectation.class,
                          new ExpectationExecutorFactory() {
                              @Override
                              @SuppressWarnings("unchecked")
                              public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(
                                                                                                       Agent<R, T, A> agent,
                                                                                                       Expectation<R, T> expectation,
                                                                                                       ObjectMapper objectMapper,
                                                                                                       ExecutorService executorService) {
                                  return (ExpectationExecutor<R, T>) new ResponseTimeExpectationExecutor(
                                                                                                         (ResponseTimeExpectation) expectation);
                              }
                          });

        final var report = runSingleCase(registry,
                                         "Any input",
                                         "Valid Response",
                                         List.of(stringLengthExp, responseTimeExp));

        assertEquals(1, report.getPassedTestCases());
        assertEquals(EvalStatus.PASSED, report.getTestCaseReports().get(0).getStatus());
        assertEquals(2, report.getTestCaseReports().get(0).getExpectationReports().size());
        assertTrue(report.getTestCaseReports().get(0)
                .getExpectationReports()
                .stream()
                .allMatch(expectationReport -> expectationReport.getStatus() == EvalStatus.PASSED));
    }

    @Test
    void testCustomExpectationBoundaryConditions() {
        final var expectation = new StringLengthExpectation(5, 10);

        // Exactly at minimum boundary (length 5)
        assertPassed(runSingleCase("case-min-boundary", "12345", expectation));

        // Exactly at maximum boundary (length 10)
        assertPassed(runSingleCase("case-max-boundary", "1234567890", expectation));

        // Just below minimum (length 4)
        assertFailed(runSingleCase("case-below-min", "1234", expectation));

        // Just above maximum (length 11)
        assertFailed(runSingleCase("case-above-max", "12345678901", expectation));
    }


    private void assertFailed(com.phonepe.sentinelai.evals.EvalReport report) {
        assertEquals(0, report.getPassedTestCases());
        assertEquals(1, report.getFailedTestCases());
        assertEquals(EvalStatus.FAILED, report.getTestCaseReports().get(0).getStatus());
    }

    private void assertPassed(com.phonepe.sentinelai.evals.EvalReport report) {
        assertEquals(1, report.getPassedTestCases());
        assertEquals(0, report.getFailedTestCases());
        assertEquals(EvalStatus.PASSED, report.getTestCaseReports().get(0).getStatus());
    }

    /**
     * STEP 3: Custom Expectation Usage - Create Registry with Custom Expectation
     *
     * To use your custom expectation, register it with the ExpectationExecutorRegistry.
     * You can create a new registry or extend an existing one.
     */
    private ExpectationExecutorRegistry createRegistryWithCustomExpectation() {
        return ExpectationExecutorRegistry.withDefaults()
                .register(StringLengthExpectation.class,
                          new ExpectationExecutorFactory() {
                              @Override
                              @SuppressWarnings("unchecked")
                              public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(
                                                                                                       Agent<R, T, A> agent,
                                                                                                       Expectation<R, T> expectation,
                                                                                                       ObjectMapper objectMapper,
                                                                                                       ExecutorService executorService) {
                                  return (ExpectationExecutor<R, T>) new StringLengthExpectationExecutor(
                                                                                                         (StringLengthExpectation) expectation);
                              }
                          });
    }

    private com.phonepe.sentinelai.evals.EvalReport runSingleCase(ExpectationExecutorRegistry registry,
                                                                  String datasetName,
                                                                  String modelOutput,
                                                                  List<Expectation<String, String>> expectations) {
        final var evalEngine = new EvalEngine(TestFactory.mapper(), registry);
        final var dataset = new Dataset<>(datasetName,
                                          List.of(new TestCase<String, String>("custom-input", expectations)));
        return evalEngine.run(dataset, TestFactory.testAgent(modelOutput));
    }

    private com.phonepe.sentinelai.evals.EvalReport runSingleCase(String datasetName,
                                                                  String modelOutput,
                                                                  Expectation<String, String> expectation) {
        return runSingleCase(createRegistryWithCustomExpectation(),
                             datasetName,
                             modelOutput,
                             List.of(expectation));
    }
}
