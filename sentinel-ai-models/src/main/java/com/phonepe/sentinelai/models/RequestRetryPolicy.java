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

package com.phonepe.sentinelai.models;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.time.Duration;
import java.util.Set;

/**
 * Retry policy of the model calls. The default does not retry: one attempt, identical to the
 * engine behavior without a policy.
 *
 * <p>Retries apply to the HTTP call only and only before the first byte of the response body is
 * consumed. A streaming response that already delivered events is not retried.
 *
 * <p>Retries trigger on network {@code IOException}s and on the configured HTTP status codes.
 * When a retried response carries a {@code Retry-After} header, the engine honors it over the
 * computed backoff.
 */
@Value
@Builder
public class RequestRetryPolicy {

    /**
     * Single attempt, no retry; this is the default.
     */
    public static final RequestRetryPolicy DEFAULT = RequestRetryPolicy.builder().build();

    /**
     * HTTP status codes that trigger a retry.
     */
    @NonNull
    @Builder.Default
    Set<Integer> retryOnStatus = Set.of(429, 500, 502, 503, 504);

    /**
     * Maximum attempts including the first; {@code 1} or less means no retry.
     */
    @Builder.Default
    int maxAttempts = 1;

    Duration initialDelay;

    Duration maxDelay;

    /**
     * Backoff multiplier per attempt; {@code 1} means fixed delay.
     */
    @Builder.Default
    double delayFactor = 1.0;

    /**
     * Honor the {@code Retry-After} header over the computed backoff when present.
     */
    @Builder.Default
    boolean honorRetryAfter = true;

    /**
     * @return True when more than one attempt is configured.
     */
    public boolean retriesEnabled() {
        return maxAttempts > 1;
    }
}
