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

    /**
     * Creates the default answer-relevance metric.
     *
     * @param <T> input/request type
     * @return answer-relevance metric using the built-in judge prompt
     */
    public static <T> Metric<String, T> answerRelevance() {
        return new OutputRelevanceMetric<>();
    }

    /**
     * Creates an answer-relevance metric with a custom judge prompt.
     *
     * @param promptTemplate prompt template containing the required placeholders
     * @param <T>            input/request type
     * @return answer-relevance metric definition
     */
    public static <T> Metric<String, T> answerRelevance(String promptTemplate) {
        return new OutputRelevanceMetric<>(promptTemplate);
    }

    /**
     * Creates a similarity-based topical relevance metric.
     * The embedding model is provided at execution time via
     * {@link com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry}.
     *
     * @param <T> input/request type
     * @return output relevance metric based on embedding similarity
     */
    public static <T> Metric<String, T> outputRelevanceBySimilarity() {
        return new OutputRelevanceBySimilarityMetric<>();
    }

    /**
     * Creates a reference-answer similarity metric.
     * The embedding model is provided at execution time via
     * {@link com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry}.
     *
     * @param referenceText reference answer to compare against
     * @param <T>           input/request type
     * @return output similarity metric definition
     */
    public static <T> Metric<String, T> outputSimilarity(String referenceText) {
        return new OutputSimilarityMetric<>(referenceText);
    }
}
