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

import com.google.common.base.Preconditions;

import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * Runtime options that control how an {@link EvalEngine} executes a dataset.
 *
 * <p>Supports fail-fast behaviour, deterministic sampling, and timeout configuration.
 */
@Builder
@Getter
public class EvalRunConfig {
    /** Whether execution should stop after the first failed test case. */
    @Builder.Default
    boolean failFast = false;

    /** Percentage of dataset cases to sample for execution, in the range {@code (0, 100]}. */
    @Builder.Default
    double samplePercentage = 100D;

    /** Seed used when shuffling test cases before sampling. */
    @Builder.Default
    long sampleSeed = 0L;

    /** Default timeout applied to test cases that do not specify their own timeout. */
    @Builder.Default
    Duration defaultTestCaseTimeout = Duration.of(30, ChronoUnit.SECONDS);

    /** Minimum number of test cases to execute even when percentage-based sampling is small. */
    @Builder.Default
    int minimumSampleSize = 1;

    /**
     * Creates a validated runtime configuration.
     *
     * @param failFast               whether execution should stop after the first failed test case
     * @param samplePercentage       percentage of dataset cases to sample for execution
     * @param sampleSeed             deterministic seed used for sampling order
     * @param defaultTestCaseTimeout default timeout for test cases without an explicit timeout
     * @param minimumSampleSize      minimum number of sampled test cases to execute
     */
    public EvalRunConfig(boolean failFast,
                         double samplePercentage,
                         long sampleSeed,
                         Duration defaultTestCaseTimeout,
                         int minimumSampleSize) {
        Preconditions.checkArgument(samplePercentage > 0 && samplePercentage <= 100,
                                    "samplePercentage must be in (0, 100]");
        Preconditions.checkArgument(!defaultTestCaseTimeout.isNegative(), "defaultTestCaseTimeout must be positive");
        Preconditions.checkArgument(minimumSampleSize > 0, "minimumSampleSize must be positive");

        this.failFast = failFast;
        this.samplePercentage = samplePercentage;
        this.sampleSeed = sampleSeed;
        this.defaultTestCaseTimeout = defaultTestCaseTimeout;
        this.minimumSampleSize = minimumSampleSize;
    }

    /**
     * Returns the default eval runtime configuration.
     *
     * @return a config instance populated with built-in defaults
     */
    public static EvalRunConfig defaults() {
        return EvalRunConfig.builder().build();
    }
}
