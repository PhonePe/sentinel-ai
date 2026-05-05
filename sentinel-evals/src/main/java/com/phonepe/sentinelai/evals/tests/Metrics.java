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

import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.embedding.EmbeddingModel;
import com.phonepe.sentinelai.evals.tests.metrics.Metric;
import com.phonepe.sentinelai.evals.tests.metrics.OutputRelevanceBySimilarityMetric;
import com.phonepe.sentinelai.evals.tests.metrics.OutputRelevanceMetric;
import com.phonepe.sentinelai.evals.tests.metrics.OutputSimilarityMetric;

import lombok.experimental.UtilityClass;

/**
 * Factory helpers for creating metrics.
 */
@UtilityClass
public class Metrics {

    public static <T> Metric<String, T> answerRelevance(Model evaluatorModel) {
        return new OutputRelevanceMetric<>(evaluatorModel);
    }

    public static <T> Metric<String, T> answerRelevance(Model evaluatorModel,
                                                        String promptTemplate) {
        return new OutputRelevanceMetric<>(evaluatorModel, promptTemplate);
    }

    public static <T> Metric<String, T> outputRelevanceBySimilarity(EmbeddingModel embeddingModel) {
        return new OutputRelevanceBySimilarityMetric<>(embeddingModel);
    }

    public static <T> Metric<String, T> outputSimilarity(EmbeddingModel embeddingModel,
                                                         String referenceText) {
        return new OutputSimilarityMetric<>(embeddingModel, referenceText);
    }
}
