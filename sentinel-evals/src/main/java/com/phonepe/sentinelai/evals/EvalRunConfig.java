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

@Builder
@Getter
public class EvalRunConfig {
    @Builder.Default
    boolean failFast = false;

    @Builder.Default
    double samplePercentage = 100D;

    @Builder.Default
    long sampleSeed = 0L;

    @Builder.Default
    Duration defaultTestCaseTimeout = Duration.of(10, ChronoUnit.SECONDS);

    @Builder.Default
    int minimumSampleSize = 1;

    public EvalRunConfig(boolean failFast,
                         double samplePercentage,
                         long sampleSeed,
                         Duration defaultTestCaseTimeout,
                         int minimumSampleSize) {
        Preconditions.checkArgument(samplePercentage > 0 && samplePercentage <= 100,
                                    "samplePercentage must be in (0, 100]");
        Preconditions.checkArgument(!defaultTestCaseTimeout.isNegative(), "defaultTestCaseTimeout must be");
        Preconditions.checkArgument(minimumSampleSize > 0, "minimumSampleSize must be positive");

        this.failFast = failFast;
        this.samplePercentage = samplePercentage;
        this.sampleSeed = sampleSeed;
        this.defaultTestCaseTimeout = defaultTestCaseTimeout;
        this.minimumSampleSize = minimumSampleSize;
    }

    public static EvalRunConfig defaults() {
        return EvalRunConfig.builder().build();
    }
}
