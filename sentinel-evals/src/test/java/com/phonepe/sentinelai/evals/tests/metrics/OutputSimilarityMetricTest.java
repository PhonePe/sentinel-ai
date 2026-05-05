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

class OutputSimilarityMetricTest {

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

    private static EvalExpectationContext<String> emptyContext() {
        return new EvalExpectationContext<>("run-id",
                                            "input",
                                            List.of(),
                                            new ModelUsageStats());
    }

    @Test
    void calculateClampsNegativeCosineToZero() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                1f, 0f
        },
                                                        "reference",
                                                        new float[]{
                                                                -1f, 0f
                                                        }));
        final var metric = new OutputSimilarityMetric<String>(model, "reference");

        final var score = metric.calculate("result", emptyContext());

        assertEquals(0.0, score, EPSILON);
    }

    @Test
    void calculateReturnsOneForIdenticalVectors() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                1f, 0f
        },
                                                        "reference",
                                                        new float[]{
                                                                1f, 0f
                                                        }));
        final var metric = new OutputSimilarityMetric<String>(model, "reference");

        final var score = metric.calculate("result", emptyContext());

        assertEquals(1.0, score, EPSILON);
        assertEquals("OutputSimilarity", metric.metricName());
    }

    @Test
    void calculateReturnsZeroForOrthogonalVectors() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                0f, 1f
        },
                                                        "reference",
                                                        new float[]{
                                                                1f, 0f
                                                        }));
        final var metric = new OutputSimilarityMetric<String>(model, "reference");

        final var score = metric.calculate("result", emptyContext());

        assertEquals(0.0, score, EPSILON);
    }

    @Test
    void calculateReturnsZeroForZeroMagnitudeVectors() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                0f, 0f
        },
                                                        "reference",
                                                        new float[]{
                                                                1f, 0f
                                                        }));
        final var metric = new OutputSimilarityMetric<String>(model, "reference");

        final var score = metric.calculate("result", emptyContext());

        assertEquals(0.0, score, EPSILON);
    }

    @Test
    void calculateReturnsZeroWhenAnyEmbeddingIsNull() {
        final var map = new HashMap<String, float[]>();
        map.put("reference", new float[]{
                1f, 0f
        });
        map.put("result", null);
        final var model = new TestEmbeddingModel(map);
        final var metric = new OutputSimilarityMetric<String>(model, "reference");

        final var score = metric.calculate("result", emptyContext());

        assertEquals(0.0, score, EPSILON);
    }

    @Test
    void calculateReturnsZeroWithoutEmbeddingLookupForNullOrEmptyResult() {
        final var model = new TestEmbeddingModel(Map.of("reference", new float[]{
                1f, 0f
        }));
        final var metric = new OutputSimilarityMetric<String>(model, "reference");

        assertEquals(0.0, metric.calculate(null, emptyContext()), EPSILON);
        assertEquals(0.0, metric.calculate("", emptyContext()), EPSILON);
        assertEquals(0, model.lookupCount);
    }

    @Test
    void calculateUsesMinLengthForMismatchedVectors() {
        final var model = new TestEmbeddingModel(Map.of("result", new float[]{
                1f, 0f, 1f
        },
                                                        "reference",
                                                        new float[]{
                                                                1f, 0f
                                                        }));
        final var metric = new OutputSimilarityMetric<String>(model, "reference");

        final var score = metric.calculate("result", emptyContext());

        assertEquals(Math.sqrt(0.5), score, EPSILON);
    }

    @Test
    void constructorValidatesInputs() {
        final var model = new TestEmbeddingModel(Map.of("reference", new float[]{
                1f, 0f
        }));

        assertThrows(IllegalArgumentException.class, () -> new OutputSimilarityMetric<String>(null, "reference"));
        assertThrows(IllegalArgumentException.class, () -> new OutputSimilarityMetric<>(model, null));
        assertThrows(IllegalArgumentException.class, () -> new OutputSimilarityMetric<>(model, ""));
    }
}
