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

import com.phonepe.sentinelai.evals.EvalStatus;
import com.phonepe.sentinelai.evals.ExpectationReport;

/**
 * Performs the computation for a corresponding {@link Expectation} definition.
 *
 * Executors are created by an {@link ExpectationExecutorFactory} and encapsulate all
 * evaluation logic, keeping {@link Expectation} classes as pure data definitions.
 *
 * @param <R> result/output type being evaluated
 * @param <T> input/request type
 */
public interface ExpectationExecutor<R, T> {

    /**
     * Evaluate the expectation against the result.
     *
     * @param result  the output/result to evaluate
     * @param context evaluation context containing request, messages, and usage stats
     * @return true if expectation passes, false otherwise
     */
    boolean evaluate(R result, EvalExpectationContext<T> context);

    /**
     * Evaluate the expectation and generate a detailed report.
     * Defaults to a simple pass/fail report based on {@link #evaluate}.
     *
     * @param result  the output/result to evaluate
     * @param context evaluation context containing request, messages, and usage stats
     * @return expectation report with status and details
     */
    default ExpectationReport evaluateWithReport(R result, EvalExpectationContext<T> context) {
        final boolean passes = evaluate(result, context);
        return ExpectationReport.builder()
                .expectation(toString())
                .status(passes ? EvalStatus.PASSED : EvalStatus.FAILED)
                .details(passes ? "Expectation passed" : "Expectation failed")
                .build();
    }
}
