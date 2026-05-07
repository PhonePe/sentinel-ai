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

import org.junit.jupiter.api.Assertions;

import com.phonepe.sentinelai.evals.EvalReport;
import com.phonepe.sentinelai.evals.EvalStatus;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Test assertion helpers for rendering human-readable diagnostics from {@link EvalReport}.
 */
public final class EvalReportAssertions {

    private EvalReportAssertions() {
    }

    public static void assertNoFailures(EvalReport report) {
        final var hasFailures = report.getFailedTestCases() > 0 || report.getSkippedTestCases() > 0;
        if (!hasFailures) {
            return;
        }

        final var diagnostics = IntStream.range(0, report.getTestCaseReports().size())
                .mapToObj(index -> {
                    final var testCaseReport = report.getTestCaseReports().get(index);
                    if (testCaseReport.getStatus() == EvalStatus.PASSED) {
                        return null;
                    }

                    final var expectationDiagnostics = testCaseReport.getExpectationReports()
                            .stream()
                            .filter(expectationReport -> expectationReport.getStatus() != EvalStatus.PASSED)
                            .map(expectationReport -> "    - [" + expectationReport.getStatus() + "] "
                                    + expectationReport.getExpectation() + " :: "
                                    + expectationReport.getDetails())
                            .collect(Collectors.joining(System.lineSeparator()));

                    final var renderedExpectations = expectationDiagnostics.isBlank()
                            ? "    - (no expectation-level diagnostics captured)"
                            : expectationDiagnostics;

                    return "TestCase #%d [%s]\n"
                            .formatted(index + 1, testCaseReport.getStatus())
                            + "  input: %s\n".formatted(String.valueOf(testCaseReport.getInput()))
                            + "  details: %s\n".formatted(testCaseReport.getDetails())
                            + "  expectations:\n"
                            + renderedExpectations;
                })
                .filter(line -> line != null)
                .collect(Collectors.joining("\n\n"));

        Assertions.fail("Eval run failed\n"
                + "dataset: %s\n".formatted(report.getDatasetName())
                + "executed=%d passed=%d failed=%d skipped=%d\n\n"
                        .formatted(report.getExecutedTestCases(),
                                   report.getPassedTestCases(),
                                   report.getFailedTestCases(),
                                   report.getSkippedTestCases())
                + diagnostics);
    }
}
