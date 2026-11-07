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

import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.evals.EvalStatus;
import com.phonepe.sentinelai.evals.ExpectationReport;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.expectations.Operator;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorFactory;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExpectation;

import lombok.val;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;

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
    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;

    /**
     * Creates an executor for a metric-backed expectation.
     *
     * @param expectation           expectation definition to evaluate
     * @param metricExecutorFactory factory used to resolve the metric executor
     * @param objectMapper          mapper provided by eval engine
     * @param executorService       executor provided by eval engine
     */
    public MetricExpectationExecutor(MetricExpectation<R, T> expectation,
                                     MetricExecutorFactory metricExecutorFactory,
                                     ObjectMapper objectMapper,
                                     ExecutorService executorService) {
        this.expectation = expectation;
        this.metricExecutorFactory = metricExecutorFactory;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.executorService = Objects.requireNonNull(executorService, "executorService cannot be null");
    }

    /**
     * Evaluates the underlying metric and converts the report status into a boolean result.
     *
     * @param result  output being evaluated
     * @param context evaluation context containing request and message history
     * @return {@code true} when the metric report is passed
     */
    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        return evaluateWithReport(result, context).getStatus() == EvalStatus.PASSED;
    }

    /**
     * Evaluates the metric and returns a detailed expectation report.
     *
     * @param result  output being evaluated
     * @param context evaluation context containing request and message history
     * @return metric expectation report with pass/fail or skipped status
     */
    @Override
    public ExpectationReport evaluateWithReport(R result, EvalExpectationContext<T> context) {
        final var metricExecutor = metricExecutorFactory.create(expectation.getMetric(), objectMapper, executorService);
        final double score;
        try {
            score = metricExecutor.calculate(result, context);
        }
        catch (Exception e) {
            return ExpectationReport.builder()
                    .expectation(expectation.getId())
                    .status(EvalStatus.SKIPPED)
                    .details("Metric evaluation skipped due to exception: " + e.getMessage())
                    .build();
        }
        if (expectation.getThreshold() == null) {
            return ExpectationReport.builder()
                    .expectation(expectation.getId())
                    .status(EvalStatus.PASSED)
                    .details("Metric evaluated without threshold enforcement (score: " + String.format("%.2f", score)
                            + ")")
                    .score(Optional.of(score))
                    .build();
        }
        val operator = expectation.getOperator() != null ? expectation.getOperator() : Operator.GTE;
        boolean passed = operator.compare(score, expectation.getThreshold());
        final var details = "Metric evaluation with threshold comparison"
                + " (score: " + String.format("%.2f", score)
                + ", threshold: " + String.format("%.2f", expectation.getThreshold()) + ")";
        return ExpectationReport.builder()
                .expectation(expectation.getId())
                .status(passed ? EvalStatus.PASSED : EvalStatus.FAILED)
                .details(details)
                .score(Optional.of(score))
                .threshold(Optional.of(expectation.getThreshold()))
                .build();
    }
}
