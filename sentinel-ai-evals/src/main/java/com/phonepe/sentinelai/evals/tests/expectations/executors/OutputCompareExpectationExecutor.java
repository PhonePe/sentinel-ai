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

import com.phonepe.sentinelai.evals.EvalStatus;
import com.phonepe.sentinelai.evals.ExpectationReport;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.OutputCompareExpectation;

import lombok.RequiredArgsConstructor;

/**
 * Executor for {@link OutputCompareExpectation} that compares the entire output value
 * using the configured {@link Operator}.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
@RequiredArgsConstructor
public class OutputCompareExpectationExecutor<R, T> implements ExpectationExecutor<R, T> {

    private final OutputCompareExpectation<R, T> expectation;

    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        return expectation.getOperator().compare(result, expectation.getExpectedValue());
    }

    @Override
    public ExpectationReport evaluateWithReport(R result, EvalExpectationContext<T> context) {
        boolean passed = evaluate(result, context);
        return ExpectationReport.builder()
                .expectation(expectation.getId())
                .status(passed ? EvalStatus.PASSED : EvalStatus.FAILED)
                .details(passed ? "output comparison passed" : "output comparison failed")
                .build();
    }
}
