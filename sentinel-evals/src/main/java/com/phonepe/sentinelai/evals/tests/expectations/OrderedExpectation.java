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

import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.Expectation;
import com.phonepe.sentinelai.evals.tests.MessageExpectation;

import java.util.List;

public class OrderedExpectation<R, T> implements Expectation<R, T> {
    private List<MessageExpectation<R, T>> expectations;

    public OrderedExpectation(List<MessageExpectation<R, T>> expectations) {
        this.expectations = expectations;
    }

    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        int expectationIndex = 0;
        for (int i = 0; i < context.getOldMessages().size() && expectationIndex < expectations.size(); i++) {
            if (expectations.get(expectationIndex).matches(context.getOldMessages().get(i))) {
                expectationIndex++;
            }
        }
        return expectationIndex == expectations.size();
    }
}
