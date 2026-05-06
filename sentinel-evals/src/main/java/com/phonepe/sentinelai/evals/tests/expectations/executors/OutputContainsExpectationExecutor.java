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

package com.phonepe.sentinelai.evals.tests.expectations.executors;

import com.google.common.base.Strings;

import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.OutputContainsExpectation;

import java.util.Locale;

/**
 * Executor for {@link OutputContainsExpectation} – checks that the output string
 * contains the expected substring (case-insensitive).
 *
 * @param <T> input/request type
 */
public class OutputContainsExpectationExecutor<T> implements ExpectationExecutor<String, T> {

    private final OutputContainsExpectation<T> expectation;

    public OutputContainsExpectationExecutor(OutputContainsExpectation<T> expectation) {
        this.expectation = expectation;
    }

    @Override
    public boolean evaluate(String result, EvalExpectationContext<T> context) {
        if (result == null) {
            return Strings.isNullOrEmpty(expectation.getContainsSubstring());
        }
        return result.toLowerCase(Locale.ROOT)
                .contains(expectation.getContainsSubstring().toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return expectation.toString();
    }
}
