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

    public static OutputValidationResults failure(String... failure) {
        return new OutputValidationResults(toFailureList(failure));
    }

    public static OutputValidationResults success() {
        return new OutputValidationResults();
    }

    private static ValidationFailure toFailure(String f) {
        return new ValidationFailure(FailureType.RETRYABLE, f);
    }

    private static List<ValidationFailure> toFailureList(String[] failure) {
        return Arrays.stream(failure)
                .map(OutputValidationResults::toFailure)
                .toList();
    }

    public OutputValidationResults addFailure(String failure) {
        this.failures.add(toFailure(failure));
        return this;
    }

    public OutputValidationResults addFailures(Collection<String> failures) {
        this.failures.addAll(Objects.requireNonNullElseGet(failures,
                                                           List::<String>of)
                .stream()
                .map(OutputValidationResults::toFailure)
                .toList());
        return this;
    }

    public boolean isRetriable() {
        return !isSuccessful() && failures.stream()
                .allMatch(result -> result.getType()
                        .equals(FailureType.RETRYABLE));
    }

    public boolean isSuccessful() {
        return failures.isEmpty();
    }
}
