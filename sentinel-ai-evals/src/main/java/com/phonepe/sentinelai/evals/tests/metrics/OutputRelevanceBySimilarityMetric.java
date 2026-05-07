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
 * Definition for a metric that measures semantic relevance between output and input.
 *
 * Carries only configuration (embedding model).
 * Computation is performed by {@code OutputRelevanceBySimilarityMetricExecutor}.
 *
 * @param <T> input/request type
 */
public class OutputRelevanceBySimilarityMetric<T> implements Metric<String, T> {

    private final EmbeddingModel embeddingModel;

    /**
     * Creates a relevance metric comparing the output with the original request via embeddings.
     *
     * @param embeddingModel embedding model used to vectorize text
     */
    public OutputRelevanceBySimilarityMetric(EmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            throw new IllegalArgumentException("embeddingModel cannot be null");
        }
        this.embeddingModel = embeddingModel;
    }

    /**
     * Returns the embedding model used by this metric.
     *
     * @return embedding model
     */
    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Returns the stable display name of this metric.
     *
     * @return metric name
     */
    @Override
    public String metricName() {
        return "OutputRelevance";
    }
}
