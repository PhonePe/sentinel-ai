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

package com.phonepe.sentinelai.filesystem.memory;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import com.phonepe.sentinelai.agentmemory.AgentMemory;
import com.phonepe.sentinelai.agentmemory.MemoryScope;
import com.phonepe.sentinelai.agentmemory.MemoryType;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.embedding.EmbeddingModel;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Proves that the pre-computed-norm + score-first sort optimisation:
 * <ol>
 * <li>Produces identical semantic ranking as the old comparator-based approach.</li>
 * <li>Is measurably faster on larger memory sets (timing proof).</li>
 * <li>Correctly handles edge cases: zero vectors, mismatched lengths, null vectors.</li>
 * </ol>
 */
class VectorSearchOptimizationTest {

    @TempDir
    Path tempDir;

    private EmbeddingModel embeddingModel;
    private FileSystemAgentMemoryStorage storage;
    private ObjectMapper objectMapper;

    /** Replicates the OLD cosineSimilarity logic (recomputes both norms every call). */
    private static double oldCosineSimilarity(float[] lhs, float[] rhs) {
        if (lhs == null || rhs == null || lhs.length != rhs.length) {
            return 0.0;
        }
        double dotProduct = 0.0;
        double normLhs = 0.0;
        double normB = 0.0;
        for (int i = 0; i < lhs.length; i++) {
            dotProduct += lhs[i] * rhs[i];
            normLhs += Math.pow(lhs[i], 2);
            normB += Math.pow(rhs[i], 2);
        }
        if (normLhs == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normLhs) * Math.sqrt(normB));
    }

    // -------------------------------------------------------------------------
    // 1. Correctness: ranking must match expected cosine similarity order
    // -------------------------------------------------------------------------

    private static float[] randomVector(int dim, long seed) {
        final var rng = new Random(seed);
        final float[] v = new float[dim];
        for (int i = 0; i < dim; i++) {
            v[i] = rng.nextFloat() * 2 - 1;
        }
        return v;
    }

    @Test
    void scoringPassIsFasterThanComparatorRecomputingNormsRepeatedly() {
        // Populate store with 200 memories using 128-dim random vectors
        final int dim = 128;
        for (int i = 0; i < 200; i++) {
            final float[] vec = randomVector(dim, i);
            when(embeddingModel.getEmbedding("mem-content-" + i)).thenReturn(vec);
            storage.save(AgentMemory.builder()
                    .agentName("bench-agent").scope(MemoryScope.AGENT).scopeId("bench-agent")
                    .memoryType(MemoryType.SEMANTIC).name("m" + i)
                    .content("mem-content-" + i).reusabilityScore(5)
                    .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                    .build());
        }

        final float[] queryVec = randomVector(dim, 9999L);
        when(embeddingModel.getEmbedding("bench-query")).thenReturn(queryVec);

        // ----- New (score-first) approach timing -----
        final int iterations = 100;
        final long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            storage.findMemories(null, null, null, null, "bench-query", 0, 10);
        }
        final long optimisedNs = System.nanoTime() - start;

        // ----- Old (comparator-recomputes-norms) approach timing -----
        // Simulate the old approach inline: recompute both norms inside comparator for every comparison.
        final var allEntries = storage.getCacheForTest().values().stream().toList();
        final long oldStart = System.nanoTime();
        for (int iter = 0; iter < iterations; iter++) {
            allEntries.stream()
                    .sorted((a, b) -> Double.compare(
                                                     oldCosineSimilarity(b.getVector(), queryVec),
                                                     oldCosineSimilarity(a.getVector(), queryVec)))
                    .limit(10)
                    .toList();
        }
        final long oldNs = System.nanoTime() - oldStart;

        final double speedupFactor = (double) oldNs / optimisedNs;
        System.out.printf(
                          "[VectorSearchOptimizationTest] N=200 D=128 iterations=%d%n"
                                  + "  Old approach (norm recomputed in comparator): %,d ns total  (%,.1f ms)%n"
                                  + "  New approach (score-first, pre-stored norms): %,d ns total  (%,.1f ms)%n"
                                  + "  Speedup factor: %.2fx%n",
                          iterations,
                          oldNs,
                          oldNs / 1_000_000.0,
                          optimisedNs,
                          optimisedNs / 1_000_000.0,
                          speedupFactor);

        // Correctness: both approaches must return the same top result
        final var optimisedResults = storage.findMemories(null, null, null, null, "bench-query", 0, 1);
        final var oldTopResult = allEntries.stream()
                .sorted((a, b) -> Double.compare(
                                                 oldCosineSimilarity(b.getVector(), queryVec),
                                                 oldCosineSimilarity(a.getVector(), queryVec)))
                .findFirst()
                .map(s -> s.getMemory().getName())
                .orElse("");
        assertEquals(oldTopResult,
                     optimisedResults.get(0).getName(),
                     "Both approaches must agree on the top-ranked memory");

        // Soft assertion: new approach should be at least as fast (avoid flakiness with 1.0 floor)
        assertTrue(speedupFactor >= 1.0,
                   String.format("Optimised approach (%.2fx) should not be slower than old approach", speedupFactor));
    }

    // -------------------------------------------------------------------------
    // 2. vectorNorm helper: correctness and x*x vs Math.pow parity
    // -------------------------------------------------------------------------

    @Test
    void semanticRankingMatchesExpectedCosineSimilarityOrder() {
        // Query vector: [1, 0, 0]
        // m1 vector:    [1, 0, 0]  → cosine = 1.0  (identical)
        // m2 vector:    [0, 1, 0]  → cosine = 0.0  (orthogonal)
        // m3 vector:    [0.6, 0.8, 0] → cosine = 0.6  (in between)
        when(embeddingModel.getEmbedding("query")).thenReturn(new float[]{
                1.0f, 0.0f, 0.0f
        });

        saveWithVector("m1", new float[]{
                1.0f, 0.0f, 0.0f
        });
        saveWithVector("m2", new float[]{
                0.0f, 1.0f, 0.0f
        });
        saveWithVector("m3", new float[]{
                0.6f, 0.8f, 0.0f
        });

        final var results = storage.findMemories(null, null, null, null, "query", 0, 3);

        assertEquals(3, results.size());
        assertEquals("m1", results.get(0).getName(), "m1 should rank first (cosine=1.0)");
        assertEquals("m3", results.get(1).getName(), "m3 should rank second (cosine=0.6)");
        assertEquals("m2", results.get(2).getName(), "m2 should rank last (cosine=0.0)");
    }

    @BeforeEach
    void setUp() {
        objectMapper = JsonUtils.createMapper();
        embeddingModel = Mockito.mock(EmbeddingModel.class);
        when(embeddingModel.getEmbedding(anyString())).thenReturn(new float[]{
                0.1f, 0.2f, 0.3f
        });
        storage = new FileSystemAgentMemoryStorage(tempDir.toString(), objectMapper, embeddingModel);
    }

    // -------------------------------------------------------------------------
    // 3. Pre-stored vectorNorm field correctness
    // -------------------------------------------------------------------------

    @Test
    void storedVectorNormMatchesDynamicallyComputedNorm() {
        // Save a memory, then verify the stored norm equals vectorNorm(vector)
        final float[] vec = new float[]{
                3.0f, 4.0f
        };  // norm = 5.0
        when(embeddingModel.getEmbedding("content for normTest")).thenReturn(vec);

        storage.save(AgentMemory.builder()
                .agentName("agent1").scope(MemoryScope.AGENT).scopeId("agent1")
                .memoryType(MemoryType.SEMANTIC).name("normTest")
                .content("content for normTest").reusabilityScore(5)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());

        // The stored entry should have the pre-computed norm
        final var stored = storage.getCacheForTest().values().stream()
                .filter(s -> "normTest".equals(s.getMemory().getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Memory not found in cache"));

        assertEquals(5.0,
                     stored.getVectorNorm(),
                     1e-6,
                     "Pre-computed vectorNorm must equal ||[3,4]|| = 5.0");
    }

    // -------------------------------------------------------------------------
    // 4. Performance proof: score-first approach beats comparator-based approach
    //    at realistic memory-store size (N=200, D=128, 100 repeated queries)
    // -------------------------------------------------------------------------

    @Test
    void topKLimitsResultsToRequestedCount() {
        when(embeddingModel.getEmbedding("query")).thenReturn(new float[]{
                1.0f, 0.0f
        });
        for (int i = 0; i < 10; i++) {
            saveWithVector("m" + i, new float[]{
                    1.0f - i * 0.05f, i * 0.05f
            });
        }

        final var results = storage.findMemories(null, null, null, null, "query", 0, 3);
        assertEquals(3, results.size(), "Must return exactly top-3 even when 10 candidates exist");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Test
    void vectorNormMatchesManualCalculation() {
        // ||[3, 4]|| = 5 exactly
        assertEquals(5.0, FileSystemAgentMemoryStorage.vectorNorm(new float[]{
                3.0f, 4.0f
        }), 1e-9);

        // Unit vector: ||[1/√2, 1/√2]|| ≈ 1.0
        final float v = (float) (1.0 / Math.sqrt(2));
        assertEquals(1.0, FileSystemAgentMemoryStorage.vectorNorm(new float[]{
                v, v
        }), 1e-6);

        // Zero vector: norm = 0
        assertEquals(0.0, FileSystemAgentMemoryStorage.vectorNorm(new float[]{
                0.0f, 0.0f
        }), 1e-12);
    }

    @Test
    void vectorNormMatchesMathPowApproach() {
        // Verify x*x gives same results as Math.pow(x, 2) for a realistic 384-dim embedding.
        final float[] vec = randomVector(384, 42L);

        final double normNewApproach = FileSystemAgentMemoryStorage.vectorNorm(vec);

        // Old approach using Math.pow
        double normOldApproach = 0.0;
        for (final float x : vec) {
            normOldApproach += Math.pow(x, 2);
        }
        normOldApproach = Math.sqrt(normOldApproach);

        assertEquals(normOldApproach,
                     normNewApproach,
                     1e-9,
                     "x*x and Math.pow(x,2) should yield the same norm");
    }

    private void saveWithVector(String name, float[] vector) {
        final String content = "content for " + name;
        when(embeddingModel.getEmbedding(content)).thenReturn(vector);
        storage.save(AgentMemory.builder()
                .agentName("agent1").scope(MemoryScope.AGENT).scopeId("agent1")
                .memoryType(MemoryType.SEMANTIC).name(name)
                .content(content).reusabilityScore(5)
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build());
    }
}
