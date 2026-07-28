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

/**
 * Definition for a metric that measures semantic similarity between output and a reference text.
 *
 * Carries only the reference text configuration.
 * The embedding model required for computation is provided via the executor
 * (wired through {@link MetricExecutorRegistry}).
 *
 * Uses cosine similarity of embedding vectors (0.0-1.0 scale).
 *
 * @param <T> The input/request type
 */
public class OutputSimilarityMetric<T> implements Metric<String, T> {

    private final String referenceText;

    /**
     * Create similarity metric comparing output to a reference text.
     *
     * @param referenceText the reference text to compare against
     */
    public OutputSimilarityMetric(String referenceText) {
        if (referenceText == null || referenceText.isEmpty()) {
            throw new IllegalArgumentException("referenceText cannot be null or empty");
        }
        this.referenceText = referenceText;
    }

    /**
     * Returns the reference text used for similarity comparison.
     *
     * @return reference text
     */
    public String getReferenceText() {
        return referenceText;
    }

    /**
     * Returns the stable display name of this metric.
     *
     * @return metric name
     */
    @Override
    public String metricName() {
        return "OutputSimilarity";
    }
}
