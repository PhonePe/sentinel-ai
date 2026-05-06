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
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorFactory;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExpectation;

/**
 * Executor for {@link MetricExpectation} – delegates score computation to the
 * appropriate {@link com.phonepe.sentinelai.evals.tests.metrics.MetricExecutor}
 * obtained from the supplied {@link MetricExecutorFactory}, then applies the optional
 * pass/fail threshold.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public class MetricExpectationExecutor<R, T> implements ExpectationExecutor<R, T> {

    private final MetricExpectation<R, T> expectation;
    private final MetricExecutorFactory metricExecutorFactory;

    public MetricExpectationExecutor(MetricExpectation<R, T> expectation,
                                     MetricExecutorFactory metricExecutorFactory) {
        this.expectation = expectation;
        this.metricExecutorFactory = metricExecutorFactory;
    }

    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        return evaluateWithReport(result, context).getStatus() == EvalStatus.PASSED;
    }

    @Override
    public ExpectationReport evaluateWithReport(R result, EvalExpectationContext<T> context) {
        final var metricExecutor = metricExecutorFactory.create(expectation.getMetric());
        final double score;
        try {
            score = metricExecutor.calculate(result, context);
        }
        catch (Exception e) {
            return ExpectationReport.skipped(metricExecutor.metricName(),
                                             "Metric evaluation skipped due to exception: " + e.getMessage());
        }
        if (expectation.getThreshold() == null) {
            return ExpectationReport.metric(metricExecutor.metricName(),
                                            score,
                                            "Metric evaluated without threshold enforcement");
        }
        return ExpectationReport.scored(metricExecutor.metricName(),
                                        score,
                                        expectation.getThreshold(),
                                        "Metric evaluation with threshold comparison");
    }

    @Override
    public String toString() {
        return expectation.toString();
    }
}
