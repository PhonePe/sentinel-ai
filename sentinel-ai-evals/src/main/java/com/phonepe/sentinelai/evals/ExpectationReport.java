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
}
