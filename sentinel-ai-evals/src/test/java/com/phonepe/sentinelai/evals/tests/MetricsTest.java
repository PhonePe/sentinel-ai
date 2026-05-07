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

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.embedding.EmbeddingModel;
import com.phonepe.sentinelai.evals.tests.metrics.OutputRelevanceBySimilarityMetric;
import com.phonepe.sentinelai.evals.tests.metrics.OutputRelevanceMetric;
import com.phonepe.sentinelai.evals.tests.metrics.OutputSimilarityMetric;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetricsTest {

    private static class StubEmbeddingModel implements EmbeddingModel {
        @Override
        public void close() {
            // no-op
        }

        @Override
        public int dimensions() {
            return 2;
        }

        @Override
        public float[] getEmbedding(String input) {
            return new float[]{
                    1f, 0f
            };
        }
    }

    // Result of assertThrows ignored; we only verify exception is thrown, not inspect it
    @SuppressWarnings("java:S2201")
    private static void expectIllegalArgument(Runnable action) {
        assertThrows(IllegalArgumentException.class, action::run);
    }

    @Test
    // Result of assertThrows ignored; we only verify exception is thrown, not inspect it
    @SuppressWarnings("java:S2201")
    void factoryMethodsPreserveConstructorValidation() {
        assertInstanceOf(OutputRelevanceMetric.class,
                         Metrics.answerRelevance(OutputRelevanceMetric.DEFAULT_PROMPT_TEMPLATE));
    }

    @Test
    @SuppressWarnings("java:S2201")
    void factoryMethodsValidateParameters() {
        expectIllegalArgument(() -> Metrics.answerRelevance(" "));
        expectIllegalArgument(() -> Metrics.outputSimilarity(new StubEmbeddingModel(), ""));
        expectIllegalArgument(() -> Metrics.outputRelevanceBySimilarity(null));
    }

    @Test
    void outputRelevanceBySimilarityFactoryCreatesMetric() {
        assertInstanceOf(OutputRelevanceBySimilarityMetric.class,
                         Metrics.outputRelevanceBySimilarity(new StubEmbeddingModel()));
    }

    @Test
    void outputSimilarityFactoryCreatesMetric() {
        assertInstanceOf(OutputSimilarityMetric.class,
                         Metrics.outputSimilarity(new StubEmbeddingModel(),
                                                  "reference"));
    }
}
