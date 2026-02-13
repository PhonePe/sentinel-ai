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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

/**
 * Filesystem based implementation of AgentMemoryStore.
 * This is not for serious production use.
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

        return cache.values().stream()
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
                .sorted((a, b) -> {
                    if (queryVector == null) {
                        final var lhs = a.getMemory().getUpdatedAt();
                        final var rhs = b.getMemory().getUpdatedAt();
                        if (lhs == null || rhs == null) {
                            final var rhsValue = rhs == null ? 0 : 1;
                            return lhs == null ? rhsValue : -1;
                        }
                        return rhs.compareTo(lhs);
                    }
                    return Double.compare(cosineSimilarity(b.getVector(), queryVector),
                                          cosineSimilarity(a.getVector(), queryVector));
                })
                .limit(count)
                .map(StoredAgentMemory::getMemory)
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

    /**
     * Compute cosine similarity between two vectors.
     * Returns a value between -1 and 1, where 1 means identical, 0 means orthogonal, and -1 means opposite.
     * Implementation:
     * - Compute the dot product of the two vectors.
     * - Compute the magnitude (norm) of each vector.
     * - Divide the dot product by the product of the magnitudes.
     */
    private double cosineSimilarity(float[] lhs, float[] rhs) {
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
