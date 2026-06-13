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
import com.google.common.base.Strings;

import com.phonepe.sentinelai.agentmemory.AgentMemory;
import com.phonepe.sentinelai.agentmemory.AgentMemoryStore;
import com.phonepe.sentinelai.agentmemory.MemoryScope;
import com.phonepe.sentinelai.agentmemory.MemoryType;
import com.phonepe.sentinelai.embedding.EmbeddingModel;
import com.phonepe.sentinelai.filesystem.utils.FileUtils;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

/**
 * Filesystem based implementation of AgentMemoryStore.
 * This is not for serious production use.
 *
 * <p>Optimizations over the naive implementation:
 * <ol>
 * <li><b>Pre-computed vector norms</b>: Each stored vector's L2 norm is computed once at save/load
 * time and cached in {@link StoredAgentMemory}. The query vector norm is computed once per
 * {@code findMemories} call. The old approach recomputed both norms inside the sort comparator,
 * causing O(N log N &times; dim) norm operations; the new approach does O(N &times; dim) scoring
 * in a single linear pass then O(N log N) comparisons on pre-scored doubles.
 * <li><b>Score-first sort</b>: All candidates are scored in one linear pass before sorting,
 * which is more cache-friendly and avoids redundant computation inside the comparator.
 * </ol>
 */
@Slf4j
public class FileSystemAgentMemoryStorage implements AgentMemoryStore {

    private static final String MEMORY_FILE_NAME = "memory.json";
    private static final String VECTOR_FILE_NAME = "vector.json";

    @Data
    @NoArgsConstructor
    public static class StoredAgentMemory {
        private AgentMemory memory;
        private float[] vector;
        /**
         * Pre-computed L2 norm of {@code vector}. Cached to eliminate per-comparison norm
         * recomputation during semantic search scoring.
         */
        private double vectorNorm;
    }

    private final Path memoryRoot;
    private final ObjectMapper mapper;
    private final EmbeddingModel embeddingModel;
    private final ConcurrentHashMap<String, StoredAgentMemory> cache = new ConcurrentHashMap<>();
    private final StampedLock lock = new StampedLock();

    @Builder
    public FileSystemAgentMemoryStorage(@NonNull String baseDir,
                                        @NonNull ObjectMapper mapper,
                                        @NonNull EmbeddingModel embeddingModel) {
        this.memoryRoot = FileUtils.ensurePath(baseDir, true, true);
        this.mapper = mapper;
        this.embeddingModel = embeddingModel;
        loadMemories();
    }

    /**
     * Compute the L2 norm (magnitude) of a vector.
     *
     * <p>Uses {@code x * x} instead of {@code Math.pow(x, 2)} — the latter routes through a
     * general exponentiation path that is measurably slower for the square case.
     */
    static double vectorNorm(float[] v) {
        double norm = 0.0;
        for (final float x : v) {
            norm += (double) x * x;
        }
        return Math.sqrt(norm);
    }

    /**
     * Compute cosine similarity between a stored memory's vector and a query vector using
     * pre-computed norms, avoiding redundant norm recomputation on repeated calls.
     */
    private static double computeSimilarity(StoredAgentMemory stored,
                                            float[] queryVector,
                                            double queryNorm) {
        final var sv = stored.getVector();
        if (sv == null || sv.length != queryVector.length || stored.getVectorNorm() == 0.0
                || queryNorm == 0.0) {
            return 0.0;
        }
        return dotProduct(sv, queryVector) / (stored.getVectorNorm() * queryNorm);
    }

    /**
     * Compute the dot product of two equal-length vectors.
     */
    private static double dotProduct(float[] a, float[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    @Override
    @SuppressWarnings("java:S3776")
    public List<AgentMemory> findMemories(String scopeId,
                                          MemoryScope scope,
                                          Set<MemoryType> memoryTypes,
                                          List<String> topics,
                                          String query,
                                          int minReusabilityScore,
                                          int count) {
        final var queryVector = !Strings.isNullOrEmpty(query)
                ? embeddingModel.getEmbedding(query)
                : null;

        // Apply all keyword/metadata filters first (cheap), materialise the surviving set, then rank.
        // Materialising before sorting avoids running the filter predicate inside the sort comparator.
        final var filtered = cache.values().stream()
                .filter(stored -> {
                    final var memory = stored.getMemory();
                    if (scope != null && !Strings.isNullOrEmpty(scopeId)) {
                        if (memory.getScope() != scope) {
                            return false;
                        }
                        if (scope == MemoryScope.ENTITY && !scopeId.equals(memory.getScopeId())) {
                            return false;
                        }
                    }
                    if (memoryTypes != null && !memoryTypes.isEmpty()
                            && !memoryTypes.contains(memory.getMemoryType())) {
                        return false;
                    }
                    if (topics != null && !topics.isEmpty()) {
                        if (memory.getTopics() == null
                                || memory.getTopics().stream().noneMatch(topics::contains)) {
                            return false;
                        }
                    }
                    return minReusabilityScore == 0 || memory.getReusabilityScore() >= minReusabilityScore;
                })
                .toList();

        if (queryVector == null) {
            // No semantic query: sort by recency (most-recently updated first).
            return filtered.stream()
                    .sorted((a, b) -> {
                        final var lhs = a.getMemory().getUpdatedAt();
                        final var rhs = b.getMemory().getUpdatedAt();
                        if (lhs == null || rhs == null) {
                            final var rhsValue = rhs == null ? 0 : 1;
                            return lhs == null ? rhsValue : -1;
                        }
                        return rhs.compareTo(lhs);
                    })
                    .limit(count)
                    .map(StoredAgentMemory::getMemory)
                    .toList();
        }

        // Semantic query path.
        //
        // Key optimisation: compute the query vector's L2 norm ONCE here, outside the sort.
        // The old approach called cosineSimilarity() inside the comparator, which recomputed the
        // query norm on every invocation — O(N log N) recomputations of O(dim) work each.
        //
        // New approach: one linear scoring pass O(N × dim) using pre-stored memory norms and
        // the once-computed query norm, followed by O(N log N) sort on plain doubles.
        final double queryNorm = vectorNorm(queryVector);
        return filtered.stream()
                .map(stored -> Map.entry(stored, computeSimilarity(stored, queryVector, queryNorm)))
                .sorted(Map.Entry.<StoredAgentMemory, Double>comparingByValue().reversed())
                .limit(count)
                .map(e -> e.getKey().getMemory())
                .toList();
    }

    @Override
    @SneakyThrows
    public Optional<AgentMemory> save(AgentMemory agentMemory) {
        final var now = LocalDateTime.now();
        final var memoryToSave = agentMemory
                .withCreatedAt(Objects.requireNonNullElse(agentMemory.getCreatedAt(), now))
                .withUpdatedAt(now);
        final var id = UUID.nameUUIDFromBytes(("%s-%s-%s-%s").formatted(
                                                                        memoryToSave.getAgentName(),
                                                                        memoryToSave.getScope(),
                                                                        memoryToSave.getScopeId(),
                                                                        memoryToSave.getName()).getBytes()).toString();

        final var vector = embeddingModel.getEmbedding(memoryToSave.getContent());
        final var stored = new StoredAgentMemory();
        stored.setMemory(memoryToSave);
        stored.setVector(vector);
        stored.setVectorNorm(vectorNorm(vector));

        final var stamp = lock.writeLock();
        try {
            final var memoryDir = FileUtils.ensurePath(memoryRoot.resolve(id).toString(), true, true);
            final var memoryFile = memoryDir.resolve(MEMORY_FILE_NAME);
            final var vectorFile = memoryDir.resolve(VECTOR_FILE_NAME);

            FileUtils.write(memoryFile, mapper.writeValueAsBytes(memoryToSave), false);
            FileUtils.write(vectorFile, mapper.writeValueAsBytes(vector), false);

            cache.put(id, stored);
            return Optional.of(memoryToSave);
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    /** Package-private accessor used only by unit tests to inspect the in-memory cache. */
    ConcurrentHashMap<String, StoredAgentMemory> getCacheForTest() {
        return cache;
    }

    @SneakyThrows
    private void loadMemories() {
        try (final var paths = Files.list(memoryRoot)) {
            paths.filter(Files::isDirectory).forEach(path -> {
                try {
                    final var memoryFile = path.resolve(MEMORY_FILE_NAME);
                    final var vectorFile = path.resolve(VECTOR_FILE_NAME);
                    if (Files.exists(memoryFile) && Files.exists(vectorFile)) {
                        final var memory = mapper.readValue(memoryFile.toFile(), AgentMemory.class);
                        final var vector = mapper.readValue(vectorFile.toFile(), float[].class);
                        final var stored = new StoredAgentMemory();
                        stored.setMemory(memory);
                        stored.setVector(vector);
                        stored.setVectorNorm(vectorNorm(vector)); // pre-compute norm at load time
                        cache.put(path.getFileName().toString(), stored);
                    }
                }
                catch (Exception e) {
                    log.error("Failed to load memory from path: {}", path, e);
                }
            });
        }
    }
}
