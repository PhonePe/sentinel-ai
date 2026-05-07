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

package com.phonepe.sentinelai.evals.tests;

import com.google.common.base.Preconditions;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Single evaluation scenario consisting of an input, expectations, and an optional timeout.
 *
 * @param <R> request type accepted by the agent
 * @param <T> response type produced by the agent
 */
@Value
public class TestCase<R, T> {
    /** Input request sent to the agent. */
    R input;
    /** Expectations evaluated against the agent output and execution context. */
    List<Expectation<T, R>> expectations;
    /** Optional timeout overriding the dataset-level default timeout. */
    Duration timeout;

    /**
     * Creates a test case without an explicit timeout.
     *
     * @param input        input request sent to the agent
     * @param expectations expectations to evaluate against the result
     */
    public TestCase(R input,
                    List<Expectation<T, R>> expectations) {
        this(input, expectations, null);
    }

    /**
     * Creates a test case with an optional timeout.
     *
     * @param input        input request sent to the agent
     * @param expectations expectations to evaluate against the result
     * @param timeout      optional timeout for this test case; must be non-negative when provided
     */
    @Builder
    public TestCase(R input, List<Expectation<T, R>> expectations, Duration timeout) {
        Preconditions.checkArgument(timeout == null || !timeout.isNegative(), "Timeout must be non-negative");
        this.input = input;
        this.expectations = Objects.requireNonNullElse(expectations, new ArrayList<>());
        this.timeout = timeout;
    }
}
