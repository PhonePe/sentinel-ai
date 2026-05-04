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

package com.phonepe.sentinelai.evals;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.Expectation;
import com.phonepe.sentinelai.evals.tests.TestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class EvalEngine {

    private static <R, T> EvalExpectationContext<R> buildContext(TestCase<R, T> testCase,
                                                                 List<AgentMessage> allMessages,
                                                                 ModelUsageStats usageStats) {
        final var safeMessages = allMessages == null ? List.<AgentMessage>of() : allMessages;
        return new EvalExpectationContext<>("eval-run-" + UUID.randomUUID(),
                                            testCase.getInput(),
                                            safeMessages,
                                            Objects.requireNonNullElseGet(usageStats, ModelUsageStats::new));
    }


    private static long elapsedMs(long startTimeNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
    }


    private static <R, T> List<TestCase<R, T>> sampleTestCases(List<TestCase<R, T>> allCases,
                                                               EvalRunConfig config) {
        if (allCases.isEmpty()) {
            return List.of();
        }
        if (config.getSamplePercentage() >= 100D) {
            return List.copyOf(allCases);
        }
        final var computedSize = (int) Math.ceil(allCases.size() * config.getSamplePercentage() / 100D);
        final var sampleSize = Math.min(allCases.size(), Math.max(config.getMinimumSampleSize(), computedSize));
        final var copy = new ArrayList<>(allCases);
        Collections.shuffle(copy, new java.util.Random(config.getSampleSeed()));
        return List.copyOf(copy.subList(0, sampleSize));
    }


    public <R, T, A extends Agent<R, T, A>> EvalReport run(Dataset<R, T> dataset,
                                                           Agent<R, T, A> agent) {
        return run(dataset, agent, EvalRunConfig.defaults());
    }

    public <R, T, A extends Agent<R, T, A>> EvalReport run(Dataset<R, T> dataset,
                                                           Agent<R, T, A> agent,
                                                           EvalRunConfig config) {
        final var startTime = System.nanoTime();
        final var allCases = Objects.requireNonNullElse(dataset.getTestCases(), List.<TestCase<R, T>>of());
        final var sampledCases = sampleTestCases(allCases, config);
        final var reports = new ArrayList<TestCaseReport>();
        int passedCount = 0;
        int failedCount = 0;
        int skippedCount = 0;

        for (TestCase<R, T> testCase : sampledCases) {
            final var report = executeTestCase(agent, testCase, config);
            reports.add(report);
            if (report.getStatus() == EvalStatus.PASSED) {
                passedCount++;
            }
            else if (report.getStatus() == EvalStatus.FAILED) {
                failedCount++;
            }
            else if (report.getStatus() == EvalStatus.SKIPPED) {
                skippedCount++;
            }
            if (config.isFailFast() && report.getStatus() == EvalStatus.FAILED) {
                break;
            }
        }

        final var executedCount = reports.size();

        return EvalReport.builder()
                .datasetName(dataset.getName())
                .totalTestCases(allCases.size())
                .sampledTestCases(sampledCases.size())
                .executedTestCases(executedCount)
                .passedTestCases(passedCount)
                .failedTestCases(failedCount)
                .skippedTestCases(skippedCount)
                .durationMs(elapsedMs(startTime))
                .completedAllSampledCases(executedCount == sampledCases.size())
                .testCaseReports(List.copyOf(reports))
                .build();
    }

    private <R, T, A extends Agent<R, T, A>> TestCaseReport executeTestCase(Agent<R, T, A> agent,
                                                                            TestCase<R, T> testCase,
                                                                            EvalRunConfig config) {
        final var startTime = System.nanoTime();
        final CompletableFuture<TestCaseReport> execution = CompletableFuture.supplyAsync(() -> {
            final var output = agent.execute(AgentInput.<R>builder()
                    .request(testCase.getInput())
                    .build());

            final var expectationReports = new ArrayList<ExpectationReport>();
            if (output.getError() != null && output.getError().getErrorType() != ErrorType.SUCCESS) {
                return new TestCaseReport(testCase.getInput(),
                                          EvalStatus.FAILED,
                                          output.getData(),
                                          expectationReports,
                                          "Agent execution failed: " + output.getError().getMessage(),
                                          elapsedMs(startTime));
            }

            final var context = buildContext(testCase, output.getAllMessages(), output.getUsage());
            var status = EvalStatus.PASSED;
            var details = "All expectations passed";

            final List<Expectation<T, R>> expectations = testCase.getExpectations() == null
                    ? List.of()
                    : testCase.getExpectations();
            for (Expectation<T, R> expectation : expectations) {
                final boolean passes = expectation.evaluate(output.getData(), context);
                final var expectationName = expectation.toString();
                if (passes) {
                    expectationReports.add(new ExpectationReport(expectationName,
                                                                 EvalStatus.PASSED,
                                                                 "Expectation passed"));
                }
                else {
                    expectationReports.add(new ExpectationReport(expectationName,
                                                                 EvalStatus.FAILED,
                                                                 "Expectation failed"));
                    status = EvalStatus.FAILED;
                    details = "Expectation failed: " + expectationName;
                    break;
                }
            }
            return new TestCaseReport(testCase.getInput(),
                                      status,
                                      output.getData(),
                                      expectationReports,
                                      details,
                                      elapsedMs(startTime));
        });

        final var timeout = Objects.requireNonNullElse(testCase.getTimeout(), config.getDefaultTestCaseTimeout());

        try {
            return execution.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException timeoutException) {
            execution.cancel(true);
            return new TestCaseReport(testCase.getInput(),
                                      EvalStatus.SKIPPED,
                                      null,
                                      List.of(),
                                      "Timed out after " + timeout,
                                      elapsedMs(startTime));
        }
        catch (Exception e) {
            return new TestCaseReport(testCase.getInput(),
                                      EvalStatus.FAILED,
                                      null,
                                      List.of(),
                                      "Exception during testcase execution: " + e.getMessage(),
                                      elapsedMs(startTime));
        }
    }


}
