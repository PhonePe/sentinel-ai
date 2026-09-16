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

package com.phonepe.sentinelai.evals.tests.metrics;

import com.phonepe.sentinelai.core.events.EventType;

import lombok.Value;

/**
 * A metric that measures the average latency of a specific agent event type.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
@Value
public class EventLatencyMetric<R, T> implements Metric<R, T> {
    String agentName;
    EventType eventType;
    String eventKey;

    @Override
    public String metricName() {
        return "EventLatency";
    }
}
