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

import dev.failsafe.ExecutionContext;
import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.RetryPolicy;
import dev.failsafe.function.ContextualSupplier;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.time.Duration;

/**
 * Builds the Failsafe retry executor for the model HTTP calls from a {@link RequestRetryPolicy}.
 * A disabled policy ({@code maxAttempts <= 1}) produces an executor that never retries.
 */
@UtilityClass
public class RequestRetryExecutors {

    /**
     * Builds a Failsafe executor that applies the given policy. A disabled policy returns an
     * executor without any retry policy.
     *
     * @param policy the retry policy; may be null (treated as the default, no retry)
     * @return the executor for the model HTTP calls
     */
    public FailsafeExecutor<Object> executorFor(final RequestRetryPolicy policy) {
        final var effective = policy == null ? RequestRetryPolicy.DEFAULT : policy;
        if (!effective.retriesEnabled()) {
            return Failsafe.none();
        }
        return Failsafe.with(retryPolicy(effective));
    }

    private ContextualSupplier<Object, Duration> delayFn(final RequestRetryPolicy policy) {
        final var initial = policy.getInitialDelay() == null ? Duration.ZERO : policy.getInitialDelay();
        final var factor = policy.getDelayFactor() <= 0 ? 1.0 : policy.getDelayFactor();
        return context -> {
            final var retryAfter = retryAfterDelay(context, policy);
            if (retryAfter != null) {
                return policy.getMaxDelay() == null || retryAfter.compareTo(policy.getMaxDelay()) <= 0
                        ? retryAfter
                        : policy.getMaxDelay();
            }
            var delay = initial;
            for (int i = 1; i < context.getAttemptCount(); i++) {
                delay = multiplied(delay, factor);
            }
            return policy.getMaxDelay() == null || delay.compareTo(policy.getMaxDelay()) <= 0
                    ? delay
                    : policy.getMaxDelay();
        };
    }

    private boolean isRetryable(final Throwable failure, final RequestRetryPolicy policy) {
        if (failure == null) {
            return false;
        }
        if (failure instanceof ModelHttpException httpException) {
            return policy.getRetryOnStatus().contains(httpException.getStatus());
        }
        // Network level failures are retryable; any other failure aborts the retries.
        return failure instanceof IOException;
    }

    private Duration multiplied(final Duration delay, final double factor) {
        if (factor == 1.0) {
            return delay;
        }
        return Duration.ofNanos((long) (delay.toNanos() * factor));
    }

    /**
     * Returns the Retry-After delay of the last failure when the policy honors the header.
     */
    private Duration retryAfterDelay(final ExecutionContext<Object> context, final RequestRetryPolicy policy) {
        if (!policy.isHonorRetryAfter()) {
            return null;
        }
        final var lastException = context.getLastException();
        return lastException instanceof ModelHttpException httpException
                ? httpException.getRetryAfterSeconds()
                : null;
    }

    private RetryPolicy<Object> retryPolicy(final RequestRetryPolicy policy) {
        return RetryPolicy.builder()
                .handleIf((result, failure) -> isRetryable(failure, policy))
                .withMaxAttempts(policy.getMaxAttempts())
                .withDelayFn(delayFn(policy))
                .build();
    }
}
