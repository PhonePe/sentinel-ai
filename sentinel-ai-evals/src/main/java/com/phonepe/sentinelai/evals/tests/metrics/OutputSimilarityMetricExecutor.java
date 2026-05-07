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

package com.phonepe.sentinelai.evals.tests.metrics;

import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;

/**
 * Executor for {@link OutputSimilarityMetric} – computes cosine similarity between
 * the output and the reference text using embedding vectors.
 *
 * @param <T> The input/request type
 */
public class OutputSimilarityMetricExecutor<T> implements MetricExecutor<String, T> {

    private final OutputSimilarityMetric<T> metric;

    /**
     * Creates an executor for output-similarity scoring.
     *
     * @param metric metric definition to evaluate
     */
    public OutputSimilarityMetricExecutor(OutputSimilarityMetric<T> metric) {
        this.metric = metric;
    }

    /**
     * Calculates cosine similarity between output and reference text embeddings.
     *
     * @param result  output string to evaluate
     * @param context evaluation context (unused)
     * @return similarity score in the range {@code [0.0, 1.0]}
     */
    @Override
    public double calculate(String result, EvalExpectationContext<T> context) {
        if (result == null || result.isEmpty()) {
            return 0.0;
        }

        final float[] outputEmbedding = metric.getEmbeddingModel().getEmbedding(result);
        final float[] referenceEmbedding = metric.getEmbeddingModel().getEmbedding(metric.getReferenceText());

        return SimilarityUtils.cosineSimilarity(outputEmbedding, referenceEmbedding);
    }

    /**
     * Returns the stable display name of the underlying metric.
     *
     * @return metric name
     */
    @Override
    public String metricName() {
        return metric.metricName();
    }
}
