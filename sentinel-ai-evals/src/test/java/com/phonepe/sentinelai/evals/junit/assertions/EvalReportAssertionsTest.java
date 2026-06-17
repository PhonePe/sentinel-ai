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

package com.phonepe.sentinelai.evals.junit.assertions;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.evals.EvalReport;
import com.phonepe.sentinelai.evals.EvalStatus;
import com.phonepe.sentinelai.evals.ExpectationReport;
import com.phonepe.sentinelai.evals.TestCaseReport;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvalReportAssertionsTest {

    @Test
    void assertNoFailuresFailsWhenAnyTestCaseFailed() {
        final var report = EvalReport.builder()
                .datasetName("sample")
                .totalTestCases(1)
                .sampledTestCases(1)
                .executedTestCases(1)
                .passedTestCases(0)
                .failedTestCases(1)
                .skippedTestCases(0)
                .durationMs(1)
                .completedAllSampledCases(true)
                .testCaseReports(List.of(new TestCaseReport("input",
                                                            EvalStatus.FAILED,
                                                            "output",
                                                            List.of(ExpectationReport.passFail("contains green",
                                                                                               false,
                                                                                               "Expected output to contain green")),
                                                            "Expectation failed: contains green",
                                                            1,
                                                            null)))
                .build();

        assertThrows(AssertionError.class, () -> EvalReportAssertions.assertNoFailures(report));
    }

    @Test
    void assertNoFailuresPassesWhenAllTestCasesPassed() {
        final var report = EvalReport.builder()
                .datasetName("sample")
                .totalTestCases(1)
                .sampledTestCases(1)
                .executedTestCases(1)
                .passedTestCases(1)
                .failedTestCases(0)
                .skippedTestCases(0)
                .durationMs(1)
                .completedAllSampledCases(true)
                .testCaseReports(List.of(new TestCaseReport("input",
                                                            EvalStatus.PASSED,
                                                            "output",
                                                            List.of(ExpectationReport.passFail("x", true, "ok")),
                                                            "All expectations passed",
                                                            1,
                                                            null)))
                .build();

        assertDoesNotThrow(() -> EvalReportAssertions.assertNoFailures(report));
    }
}
