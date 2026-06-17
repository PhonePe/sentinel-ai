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

import java.util.Optional;

/**
 * Report of an expectation evaluation.
 *
 * Supports both pass/fail and metric-based (scored) expectations:
 * - Pass/fail: score and threshold are empty
 * - Scored: score (0.0-1.0) and optional threshold are populated
 */
@Value
@Builder
public class ExpectationReport {
    String expectation;
    EvalStatus status;
    String details;
    @Builder.Default
    Optional<Double> score = Optional.empty();
    @Builder.Default
    Optional<Double> threshold = Optional.empty();

    public static ExpectationReport metric(String metricName, double score, String details) {
        return ExpectationReport.builder()
                .expectation(metricName)
                .status(EvalStatus.PASSED)
                .details(details + " (score: " + String.format("%.2f", score) + ")")
                .score(Optional.of(score))
                .build();
    }

    public static ExpectationReport passFail(String expectation, boolean passes, String details) {
        return ExpectationReport.builder()
                .expectation(expectation)
                .status(passes ? EvalStatus.PASSED : EvalStatus.FAILED)
                .details(details)
                .build();
    }

    /**
     * Factory method for metric-based (scored) expectations.
     * Status is determined by comparing score against threshold.
     *
     * @param expectation name/description of the expectation
     * @param score       numeric score (0.0-1.0)
     * @param threshold   minimum score required to pass
     * @param details     explanation of the result
     * @return ExpectationReport with status set based on score >= threshold
     */
    public static ExpectationReport scored(String expectation, double score, double threshold, String details) {
        return ExpectationReport.builder()
                .expectation(expectation)
                .status(score >= threshold ? EvalStatus.PASSED : EvalStatus.FAILED)
                .details(details + " (score: " + String.format("%.2f", score)
                        + ", threshold: " + String.format("%.2f", threshold) + ")")
                .score(Optional.of(score))
                .threshold(Optional.of(threshold))
                .build();
    }

    /**
     * Factory method for skipped expectations.
     *
     * @param expectation name/description of the expectation
     * @param details     explanation of why evaluation was skipped
     * @return ExpectationReport with {@link EvalStatus#SKIPPED} status
     */
    public static ExpectationReport skipped(String expectation, String details) {
        return ExpectationReport.builder()
                .expectation(expectation)
                .status(EvalStatus.SKIPPED)
                .details(details)
                .build();
    }
}
