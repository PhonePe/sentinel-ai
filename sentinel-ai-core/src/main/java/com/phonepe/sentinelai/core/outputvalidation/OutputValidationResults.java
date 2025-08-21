package com.phonepe.sentinelai.core.outputvalidation;

import lombok.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Results of output validation
 */
@Value
public class OutputValidationResults {

    public enum FailureType {
        RETRYABLE,
        PERMANENT
    }

    @Value
    public static class ValidationFailure {
        FailureType type;
        String message;
    }

    List<ValidationFailure> failures;

    public OutputValidationResults() {
        this(List.of());
    }

    public OutputValidationResults(List<ValidationFailure> failures) {
        this.failures = new ArrayList<>(failures);
    }

    public boolean isSuccessful() {
        return failures.isEmpty();
    }

    public boolean isRetriable() {
        return !isSuccessful()
                && failures.stream()
                .allMatch(result -> result.getType().equals(FailureType.RETRYABLE));
    }

    public OutputValidationResults addFailure(String failure) {
        this.failures.add(toFailure(failure));
        return this;
    }

    public OutputValidationResults addFailures(Collection<String> failures) {
        this.failures.addAll(Objects.requireNonNullElseGet(failures, List::<String>of)
                                     .stream()
                                     .map(OutputValidationResults::toFailure)
                                     .toList());
        return this;
    }

    private static ValidationFailure toFailure(String f) {
        return new ValidationFailure(FailureType.RETRYABLE, f);
    }

    public static OutputValidationResults success() {
        return new OutputValidationResults();
    }

    public static OutputValidationResults failure(String... failure) {
        return new OutputValidationResults(toFailureList(failure));
    }

    private static List<ValidationFailure> toFailureList(String[] failure) {
        return Arrays.stream(failure)
                .map(OutputValidationResults::toFailure)
                .toList();
    }
}
