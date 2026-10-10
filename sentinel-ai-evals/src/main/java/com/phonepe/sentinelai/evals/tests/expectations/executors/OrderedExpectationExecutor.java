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

import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.OrderedExpectation;

import java.util.List;

/**
 * Executor for {@link OrderedExpectation} – verifies that the inner
 * {@link com.phonepe.sentinelai.evals.tests.MessageExpectation}s are satisfied in order
 * (though not necessarily consecutively) within the message history.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public class OrderedExpectationExecutor<R, T> implements ExpectationExecutor<R, T> {

    private final OrderedExpectation<R, T> expectation;
    private final List<MessageExpectationExecutor<?, R, T>> executors;

    /**
     * Creates an executor for ordered message expectations.
     *
     * @param expectation ordered expectation definition
     * @param executors   message executors corresponding to the ordered inner expectations
     */
    public OrderedExpectationExecutor(OrderedExpectation<R, T> expectation,
                                      List<MessageExpectationExecutor<?, R, T>> executors) {
        this.expectation = expectation;
        this.executors = executors;
    }

    /**
     * Checks whether all inner message expectations match the message history in order.
     *
     * @param result  agent output (ignored by this executor)
     * @param context evaluation context containing the message history
     * @return {@code true} when all inner expectations are satisfied in order
     */
    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        int executorIndex = 0;
        for (int i = 0; i < context.getOldMessages().size() && executorIndex < executors.size(); i++) {
            if (executors.get(executorIndex).matches(context.getOldMessages().get(i))) {
                executorIndex++;
            }
        }
        return executorIndex == executors.size();
    }
}
