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

package com.phonepe.sentinelai.evals.tests.expectations;

import com.phonepe.sentinelai.evals.tests.Expectation;
import com.phonepe.sentinelai.evals.tests.MessageExpectation;

import java.util.List;

/**
 * Expectation definition that asserts a sequence of {@link MessageExpectation}s
 * appear in order (though not necessarily consecutively) within the message history.
 *
 * Computation is performed by {@code OrderedExpectationExecutor}.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public class OrderedExpectation<R, T> implements Expectation<R, T> {

    private final List<MessageExpectation<R, T>> expectations;

    public OrderedExpectation(List<MessageExpectation<R, T>> expectations) {
        this.expectations = expectations;
    }

    public List<MessageExpectation<R, T>> getExpectations() {
        return expectations;
    }
}
