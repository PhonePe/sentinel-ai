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

package com.phonepe.sentinelai.core.agent;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.temporal.ChronoUnit;

/**
 * Granularity at which the {@code currentTime} rendered into the system prompt is truncated. Coarser granularity keeps
 * the system prompt byte-identical across successive runs for longer, improving LLM prompt-cache hit rates at the cost
 * of the model seeing a staler timestamp.
 */
@Getter
@RequiredArgsConstructor
public enum TimeGranularity {
    DAY(ChronoUnit.DAYS),
    HOURS(ChronoUnit.HOURS),
    MINUTES(ChronoUnit.MINUTES);

    private final ChronoUnit chronoUnit;
}
