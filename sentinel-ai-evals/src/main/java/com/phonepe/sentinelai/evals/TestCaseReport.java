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

import lombok.Value;

import java.util.List;

/**
 * Detailed outcome of executing a single test case.
 */
@Value
public class TestCaseReport {
    /** Input request used when invoking the agent. */
    Object input;
    /** Final status for the test case after evaluating all expectations. */
    EvalStatus status;
    /** Output returned by the agent, if available. */
    Object output;
    /** Per-expectation evaluation results in execution order. */
    List<ExpectationReport> expectationReports;
    /** Human-readable summary of the outcome. */
    String details;
    /** Test-case execution duration in milliseconds. */
    long evalDurationMs;
    /** Agent execute() latency in milliseconds, or null if unavailable. */
    Long agentLatencyMs;
}
