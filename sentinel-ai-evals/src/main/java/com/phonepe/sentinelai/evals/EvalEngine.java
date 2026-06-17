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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.Expectation;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutorFactory;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutorRegistry;
import com.phonepe.sentinelai.evals.tests.TestCase;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Executes evaluation datasets against Sentinel agents and produces aggregated reports.
 *
 * <p>The engine runs each sampled {@link TestCase}, builds an {@link EvalExpectationContext}
 * from the agent execution output, delegates each expectation to the configured
 * {@link ExpectationExecutorFactory}, and aggregates the resulting expectation-level outcomes
 * into an {@link EvalReport}.
 */
@Slf4j
public class EvalEngine {

    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final ExpectationExecutorFactory executorFactory;

    /**
     * Creates an engine with the default JSON mapper and built-in expectation registry.
     */
    public EvalEngine() {
        this(JsonUtils.createMapper());
    }

    /**
     * Creates an engine with the supplied mapper and built-in expectation registry.
     *
     * @param objectMapper mapper used for eval serialization and JSONPath evaluation
     */
    public EvalEngine(ObjectMapper objectMapper) {
        this(objectMapper, ForkJoinPool.commonPool(), ExpectationExecutorRegistry.withDefaults());
    }

    /**
     * Creates an engine with the supplied mapper/executor and built-in expectation registry.
     *
     * @param objectMapper    mapper used for eval serialization and JSONPath evaluation
     * @param executorService executor used by eval runtime and async metric evaluators
     */
    public EvalEngine(ObjectMapper objectMapper,
                      ExecutorService executorService) {
        this(objectMapper, executorService, ExpectationExecutorRegistry.withDefaults());
    }

    /**
     * Creates an engine with explicit runtime executor, serialization, and expectation execution strategies.
     *
     * @param objectMapper    mapper used to serialize structured outputs during evaluation
     * @param executorService executor used by eval runtime and async metric evaluators
     * @param executorFactory factory used to create executors for expectations in each test case
     */
    public EvalEngine(ObjectMapper objectMapper,
                      ExecutorService executorService,
                      ExpectationExecutorFactory executorFactory) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.executorService = Objects.requireNonNull(executorService, "executorService cannot be null");
        this.executorFactory = Objects.requireNonNull(executorFactory, "executorFactory cannot be null");
    }

    /**
     * Creates an engine with explicit serialization and expectation execution strategies.
     *
     * @param objectMapper    mapper used to serialize structured outputs during evaluation
     * @param executorFactory factory used to create executors for expectations in each test case
     */
    public EvalEngine(ObjectMapper objectMapper, ExpectationExecutorFactory executorFactory) {
        this(objectMapper, ForkJoinPool.commonPool(), executorFactory);
    }

    private static <R, T> EvalExpectationContext<R> buildContext(TestCase<R, T> testCase,
                                                                 List<AgentMessage> allMessages,
                                                                 ModelUsageStats usageStats,
                                                                 String modelId,
                                                                 Long latencyMs) {
        final var safeMessages = Objects.requireNonNullElse(allMessages, List.<AgentMessage>of());
        return EvalExpectationContext.<R>builder()
                .runId("eval-run-" + UUID.randomUUID())
                .request(testCase.getInput())
                .oldMessages(safeMessages)
                .modelUsageStats(Objects.requireNonNullElseGet(usageStats, ModelUsageStats::new))
                .modelId(modelId)
                .latencyMs(latencyMs)
                .build();
    }

    private static List<MetricScore> collectRawMetricScores(List<TestCaseReport> reports) {
        final List<MetricScore> scores = new ArrayList<>();
        for (TestCaseReport report : reports) {
            for (ExpectationReport expectation : report.getExpectationReports()) {
                if (expectation.getScore().isPresent()) {
                    scores.add(MetricScore.builder()
                            .metricName(expectation.getExpectation())
                            .score(expectation.getScore().get())
                            .build());
                }
            }
        }
        return scores;
    }


    private static long elapsedMs(Stopwatch stopwatch) {
        return stopwatch.elapsed(TimeUnit.MILLISECONDS);
    }

    private static <R, T, A extends Agent<R, T, A>> String resolveModelId(Agent<R, T, A> agent) {
        var setup = agent.getSetup();
        if (setup == null || setup.getModel() == null) {
            return null;
        }
        return setup.getModel().modelId();
    }

    @SuppressWarnings("java:S2245")
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


    /**
     * Runs the supplied dataset with the default runtime configuration.
     *
     * @param dataset dataset containing test cases and expectations to execute
     * @param agent   agent under evaluation
     * @param <R>     request type accepted by the agent
     * @param <T>     response type produced by the agent
     * @param <A>     concrete agent type
     * @return aggregated evaluation report for the dataset run
     */
    public <R, T, A extends Agent<R, T, A>> EvalReport run(Dataset<R, T> dataset,
                                                           Agent<R, T, A> agent) {
        Objects.requireNonNull(dataset, "dataset cannot be null");
        Objects.requireNonNull(agent, "agent cannot be null");
        return run(dataset, agent, EvalRunConfig.defaults());
    }

    /**
     * Runs the supplied dataset with the provided runtime configuration.
     *
     * @param dataset dataset containing test cases and expectations to execute
     * @param agent   agent under evaluation
     * @param config  execution options such as sampling, fail-fast behaviour, and timeouts
     * @param <R>     request type accepted by the agent
     * @param <T>     response type produced by the agent
     * @param <A>     concrete agent type
     * @return aggregated evaluation report for the dataset run
     */
    public <R, T, A extends Agent<R, T, A>> EvalReport run(Dataset<R, T> dataset,
                                                           Agent<R, T, A> agent,
                                                           EvalRunConfig config) {
        Objects.requireNonNull(dataset, "dataset cannot be null");
        Objects.requireNonNull(agent, "agent cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        final var stopwatch = Stopwatch.createStarted();
        final var allCases = Objects.requireNonNullElse(dataset.getTestCases(), List.<TestCase<R, T>>of());
        final var sampledCases = sampleTestCases(allCases, config);
        final var reports = executeAndCollectReports(agent, sampledCases, config);

        return buildReport(dataset, allCases, sampledCases, reports, stopwatch);
    }

    private <R, T> EvalReport buildReport(Dataset<R, T> dataset,
                                          List<TestCase<R, T>> allCases,
                                          List<TestCase<R, T>> sampledCases,
                                          List<TestCaseReport> reports,
                                          Stopwatch stopwatch) {
        final var executedCount = reports.size();
        final var rawMetricScores = collectRawMetricScores(reports);

        return EvalReport.builder()
                .datasetName(dataset.getName())
                .totalTestCases(allCases.size())
                .sampledTestCases(sampledCases.size())
                .executedTestCases(executedCount)
                .passedTestCases((int) reports.stream().filter(r -> r.getStatus() == EvalStatus.PASSED).count())
                .failedTestCases((int) reports.stream().filter(r -> r.getStatus() == EvalStatus.FAILED).count())
                .skippedTestCases((int) reports.stream().filter(r -> r.getStatus() == EvalStatus.SKIPPED).count())
                .durationMs(elapsedMs(stopwatch))
                .completedAllSampledCases(executedCount == sampledCases.size())
                .testCaseReports(List.copyOf(reports))
                .metricScores(rawMetricScores)
                .build();
    }

    @SuppressWarnings({
            "unchecked", "rawtypes"
    })
    private <R, T, A extends Agent<R, T, A>> ExpectationExecutor<T, R> createExecutor(Agent<R, T, A> agent,
                                                                                      Expectation<T, R> expectation) {
        return executorFactory.create((Agent) agent,
                                      expectation,
                                      objectMapper,
                                      executorService);
    }

    private <R, T> List<TestCaseReport> executeAndCollectReports(Agent<R, T, ?> agent,
                                                                 List<TestCase<R, T>> sampledCases,
                                                                 EvalRunConfig config) {
        final var reports = new ArrayList<TestCaseReport>();
        for (TestCase<R, T> testCase : sampledCases) {
            final var report = executeTestCase(agent, testCase, config);
            reports.add(report);
            if (config.isFailFast() && report.getStatus() == EvalStatus.FAILED) {
                break;
            }
        }
        return reports;
    }

    private <R, T, A extends Agent<R, T, A>> TestCaseReport executeTestCase(Agent<R, T, A> agent,
                                                                            TestCase<R, T> testCase,
                                                                            EvalRunConfig config) {
        final var callerStopwatch = Stopwatch.createStarted();
        final var execution = CompletableFuture.supplyAsync(() -> {
            final var supplierStopwatch = Stopwatch.createStarted();
            final var agentStopwatch = Stopwatch.createStarted();
            final var output = agent.execute(AgentInput.<R>builder()
                    .request(testCase.getInput())
                    .build());
            final var agentLatencyMs = elapsedMs(agentStopwatch);

            final var expectationReports = new ArrayList<ExpectationReport>();
            if (output.getError() != null && output.getError().getErrorType() != ErrorType.SUCCESS) {
                return new TestCaseReport(testCase.getInput(),
                                          EvalStatus.FAILED,
                                          output.getData(),
                                          expectationReports,
                                          "Agent execution failed: " + output.getError().getMessage(),
                                          elapsedMs(supplierStopwatch),
                                          agentLatencyMs);
            }

            final var modelId = resolveModelId(agent);
            final var context = buildContext(testCase,
                                             output.getAllMessages(),
                                             output.getUsage(),
                                             modelId,
                                             agentLatencyMs);
            var status = EvalStatus.PASSED;
            var details = "All expectations passed";

            final List<Expectation<T, R>> expectations = Objects.requireNonNullElse(testCase.getExpectations(),
                                                                                    List.of());

            var hasFailure = false;
            for (Expectation<T, R> expectation : expectations) {
                final var executor = createExecutor(agent, expectation);
                final var report = executor.evaluateWithReport(output.getData(), context);
                expectationReports.add(report);
                if (report.getStatus() == EvalStatus.FAILED) {
                    hasFailure = true;
                    if (!config.isEvaluateAllExpectations()) {
                        status = EvalStatus.FAILED;
                        details = "Expectation failed: " + report.getExpectation();
                        break;
                    }
                }
                if (report.getStatus() == EvalStatus.SKIPPED) {
                    status = EvalStatus.SKIPPED;
                    details = "Expectation skipped: " + report.getExpectation();
                    log.warn("Expectation skipped during evaluation: {}", report.getExpectation());
                }
            }

            if (hasFailure) {
                status = EvalStatus.FAILED;
                details = "One or more expectations failed";
            }
            return new TestCaseReport(testCase.getInput(),
                                      status,
                                      output.getData(),
                                      expectationReports,
                                      details,
                                      elapsedMs(supplierStopwatch),
                                      agentLatencyMs);
        }, executorService);

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
                                      elapsedMs(callerStopwatch),
                                      null);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new TestCaseReport(testCase.getInput(),
                                      EvalStatus.SKIPPED,
                                      null,
                                      List.of(),
                                      "Test case execution interrupted",
                                      elapsedMs(callerStopwatch),
                                      null);
        }
        catch (Exception e) {
            return new TestCaseReport(testCase.getInput(),
                                      EvalStatus.FAILED,
                                      null,
                                      List.of(),
                                      "Exception during testcase execution: " + e.getMessage(),
                                      elapsedMs(callerStopwatch),
                                      null);
        }
    }


}
