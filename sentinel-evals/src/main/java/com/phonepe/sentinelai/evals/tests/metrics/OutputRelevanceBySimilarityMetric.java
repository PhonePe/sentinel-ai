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
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;

/**
 * Measures semantic relevance between output and input using cosine similarity over embeddings.
 *
 * @param <T> input/request type
 */
public class OutputRelevanceBySimilarityMetric<T> implements Metric<String, T> {
    private final EmbeddingModel embeddingModel;

    public OutputRelevanceBySimilarityMetric(EmbeddingModel embeddingModel) {
        if (embeddingModel == null) {
            throw new IllegalArgumentException("embeddingModel cannot be null");
        }
        this.embeddingModel = embeddingModel;
    }

    @Override
    public double calculate(String result, EvalExpectationContext<T> context) {
        if (result == null || result.isEmpty() || context == null || context.getRequest() == null) {
            return 0.0;
        }
        final String inputText = String.valueOf(context.getRequest());
        if (inputText.isEmpty()) {
            return 0.0;
        }

        final float[] outputEmbedding = embeddingModel.getEmbedding(result);
        final float[] inputEmbedding = embeddingModel.getEmbedding(inputText);
        return SimilarityUtils.cosineSimilarity(outputEmbedding, inputEmbedding);
    }

    @Override
    public String metricName() {
        return "OutputRelevance";
    }
}
