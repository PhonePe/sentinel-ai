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

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Aggregated outcome of executing a {@code Dataset} against an agent.
 *
 * <p>Contains dataset-level counters, timing information, per-test-case reports, and any raw
 * metric scores collected during the run.
 */
@Value
@Builder
public class EvalReport {

    String datasetName;

    int totalTestCases;

    int sampledTestCases;
    /** Number of test cases actually executed before completion or fail-fast termination. */
    int executedTestCases;

    int passedTestCases;

    int failedTestCases;
    /** Number of executed test cases that were skipped, for example because of timeouts. */
    int skippedTestCases;
    /** Total wall-clock duration of the dataset run in milliseconds. */
    long durationMs;
    /** Whether every sampled test case was executed. */
    boolean completedAllSampledCases;
    /** Detailed report for each executed test case. */
    List<TestCaseReport> testCaseReports;
    /** Raw metric scores emitted while evaluating expectations. */
    @Builder.Default
    List<MetricScore> metricScores = List.of();

}
