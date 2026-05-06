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

import com.phonepe.sentinelai.embedding.EmbeddingModel;

/**
 * Definition for a metric that measures semantic similarity between output and a reference text.
 *
 * Carries only configuration (embedding model, reference text).
 * Computation is performed by {@code OutputSimilarityMetricExecutor}.
 *
 * Uses cosine similarity of embedding vectors (0.0-1.0 scale).
 *
 * @param <T> The input/request type
 */
public class OutputSimilarityMetric<T> implements Metric<String, T> {

    private final EmbeddingModel embeddingModel;
    private final String referenceText;

    /**
     * Create similarity metric comparing output to a reference text.
     *
     * @param embeddingModel the embedding model for semantic representation
     * @param referenceText  the reference text to compare against
     */
    public OutputSimilarityMetric(EmbeddingModel embeddingModel, String referenceText) {
        if (embeddingModel == null) {
            throw new IllegalArgumentException("embeddingModel cannot be null");
        }
        if (referenceText == null || referenceText.isEmpty()) {
            throw new IllegalArgumentException("referenceText cannot be null or empty");
        }
        this.embeddingModel = embeddingModel;
        this.referenceText = referenceText;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    public String getReferenceText() {
        return referenceText;
    }

    @Override
    public String metricName() {
        return "OutputSimilarity";
    }

    @Override
    public String toString() {
        return "OutputSimilarityMetric(reference="
                + referenceText.substring(0, Math.min(30, referenceText.length()))
                + "...)";
    }
}
