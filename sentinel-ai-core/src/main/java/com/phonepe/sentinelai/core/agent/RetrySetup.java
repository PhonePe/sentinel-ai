package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.errors.ErrorType;
import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration for retry
 */
@Value
@With
public class RetrySetup {
    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final Duration DEFAULT_RETRY_INTERVAL = Duration.ofSeconds(1);
    public static final Set<ErrorType> DEFAULT_RETRYABLE_ERROR_TYPES = Arrays.stream(ErrorType.values())
            .filter(ErrorType::isRetryable)
            .collect(Collectors.toUnmodifiableSet());
    public static final RetrySetup DEFAULT = RetrySetup.builder().build();

    /**
     * Number of attempts to stop after if calls keep failing.
     */
    int stopAfterAttempt;

    /**
     * Time to wait before making the next attempt.
     */
    Duration delayAfterFailedAttempt;

    /**
     * By default, we use {@link ErrorType#isRetryable()} to determine if an error can be retried. If this set is
     * provided, we use that to retry instead.
     */
    Set<ErrorType> retriableErrorTypes;


    @Builder
    public RetrySetup(int stopAfterAttempt, Duration delayAfterFailedAttempt, Set<ErrorType> retriableErrorTypes) {
        this.stopAfterAttempt = stopAfterAttempt <= 0 ? DEFAULT_MAX_ATTEMPTS : stopAfterAttempt;
        this.delayAfterFailedAttempt = Objects.requireNonNullElse(delayAfterFailedAttempt, DEFAULT_RETRY_INTERVAL);
        this.retriableErrorTypes = Objects.requireNonNullElse(retriableErrorTypes, DEFAULT_RETRYABLE_ERROR_TYPES);
    }
}
