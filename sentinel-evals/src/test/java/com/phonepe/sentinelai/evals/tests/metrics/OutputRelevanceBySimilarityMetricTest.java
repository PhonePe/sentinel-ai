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

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.embedding.EmbeddingModel;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutputRelevanceBySimilarityMetricTest {

    private static final double EPSILON = 1e-6;

    private static class TestEmbeddingModel implements EmbeddingModel {
        private final Map<String, float[]> embeddings;
        private int lookupCount;

        private TestEmbeddingModel(Map<String, float[]> embeddings) {
            this.embeddings = embeddings;
        }

        @Override
        public void close() {
            // no-op
        }

        @Override
        public int dimensions() {
            return 0;
        }

        @Override
        public float[] getEmbedding(String input) {
            lookupCount++;
            return embeddings.get(input);
        }
    }

    private static <T> EvalExpectationContext<T> context(T request) {
        return new EvalExpectationContext<>("run-id",
                                            request,
                                            List.of(),
                                            new ModelUsageStats());
    }

    @Test
    void calculateClampsNegativeCosineToZero() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                1f, 0f
        },
                                                        "request",
                                                        new float[]{
                                                                -1f, 0f
                                                        }));
        final var metric = new OutputRelevanceBySimilarityMetric<String>(model);

        final var score = metric.calculate("result", context("request"));

        assertEquals(0.0, score, EPSILON);
    }

    @Test
    void calculateReturnsOneForIdenticalVectors() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                1f, 0f
        },
                                                        "request",
                                                        new float[]{
                                                                1f, 0f
                                                        }));
        final var metric = new OutputRelevanceBySimilarityMetric<String>(model);

        final var score = metric.calculate("result", context("request"));

        assertEquals(1.0, score, EPSILON);
        assertEquals(2, model.lookupCount);
        assertEquals("OutputRelevance", metric.metricName());
    }

    @Test
    void calculateReturnsZeroForEmptyRequestTextWithoutEmbeddingLookup() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                1f, 0f
        }));
        final var metric = new OutputRelevanceBySimilarityMetric<String>(model);

        assertEquals(0.0, metric.calculate("result", context("")), EPSILON);
        assertEquals(0, model.lookupCount);
    }

    @Test
    void calculateReturnsZeroForNullContextOrRequestWithoutEmbeddingLookup() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                1f, 0f
        },
                                                        "request",
                                                        new float[]{
                                                                1f, 0f
                                                        }));
        final var metric = new OutputRelevanceBySimilarityMetric<String>(model);

        assertEquals(0.0, metric.calculate("result", null), EPSILON);
        assertEquals(0.0, metric.calculate("result", context(null)), EPSILON);
        assertEquals(0, model.lookupCount);
    }

    @Test
    void calculateReturnsZeroForNullOrEmptyResultWithoutEmbeddingLookup() {
        final var model = new TestEmbeddingModel(Map.of("request", new float[]{
                1f, 0f
        }));
        final var metric = new OutputRelevanceBySimilarityMetric<String>(model);

        assertEquals(0.0, metric.calculate(null, context("request")), EPSILON);
        assertEquals(0.0, metric.calculate("", context("request")), EPSILON);
        assertEquals(0, model.lookupCount);
    }

    @Test
    void calculateReturnsZeroForOrthogonalVectors() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                0f, 1f
        },
                                                        "request",
                                                        new float[]{
                                                                1f, 0f
                                                        }));
        final var metric = new OutputRelevanceBySimilarityMetric<String>(model);

        final var score = metric.calculate("result", context("request"));

        assertEquals(0.0, score, EPSILON);
    }

    @Test
    void calculateReturnsZeroForZeroMagnitudeVectors() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                0f, 0f
        },
                                                        "request",
                                                        new float[]{
                                                                1f, 0f
                                                        }));
        final var metric = new OutputRelevanceBySimilarityMetric<String>(model);

        final var score = metric.calculate("result", context("request"));

        assertEquals(0.0, score, EPSILON);
    }

    @Test
    void calculateReturnsZeroWhenAnyEmbeddingIsNull() {
        final var map = new HashMap<String, float[]>();
        map.put("request", new float[]{
                1f, 0f
        });
        map.put("result", null);
        final var model = new TestEmbeddingModel(map);
        final var metric = new OutputRelevanceBySimilarityMetric<String>(model);

        final var score = metric.calculate("result", context("request"));

        assertEquals(0.0, score, EPSILON);
    }

    @Test
    void calculateUsesMinLengthForMismatchedVectors() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                1f, 0f, 1f
        },
                                                        "request",
                                                        new float[]{
                                                                1f, 0f
                                                        }));
        final var metric = new OutputRelevanceBySimilarityMetric<String>(model);

        final var score = metric.calculate("result", context("request"));

        assertEquals(Math.sqrt(0.5), score, EPSILON);
    }

    @Test
    void calculateUsesStringValueOfForNonStringRequests() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                1f, 0f
        },
                                                        "42",
                                                        new float[]{
                                                                1f, 0f
                                                        }));
        final var metric = new OutputRelevanceBySimilarityMetric<Integer>(model);

        final var score = metric.calculate("result", context(42));

        assertEquals(1.0, score, EPSILON);
    }

    @Test
    void constructorValidatesInputs() {
        assertThrows(IllegalArgumentException.class, () -> new OutputRelevanceBySimilarityMetric<String>(null));
    }
}
