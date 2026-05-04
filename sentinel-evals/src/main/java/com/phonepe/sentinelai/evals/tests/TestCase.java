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

import lombok.Data;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
public class TestCase<R, T> {
    private R input;
    private List<Expectation<T, R>> expectations;
    private Duration timeout;

    public TestCase(R input,
                    List<Expectation<T, R>> expectations) {
        this(input, expectations, null);
    }

    public TestCase(R input, List<Expectation<T, R>> expectations, Duration timeout) {
        Preconditions.checkArgument(timeout == null || !timeout.isNegative(), "Timeout must be non-negative");
        this.input = input;
        this.expectations = Objects.requireNonNullElse(expectations, new ArrayList<>());
        this.timeout = timeout;
    }
}
