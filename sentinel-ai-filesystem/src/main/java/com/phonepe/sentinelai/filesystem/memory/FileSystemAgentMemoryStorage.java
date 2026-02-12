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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;

/**
 * Filesystem based implementation of AgentMemoryStore
 */
@Slf4j
public class FileSystemAgentMemoryStorage implements AgentMemoryStore {

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
    public List<AgentMemory> findMemories(String scopeId,
                                          MemoryScope scope,
                                          Set<MemoryType> memoryTypes,
                                          List<String> topics,
                                          String query,
                                          int minReusabilityScore,
                                          int count) {
        final float[] queryVector = !Strings.isNullOrEmpty(query) ? embeddingModel.getEmbedding(query) : null;

        return cache.values().stream()
                .filter(stored -> {
                    final var memory = stored.getMemory();
                    if (scope != null && !Strings.isNullOrEmpty(scopeId)) {
                        if (memory.getScope() != scope) return false;
                        if (scope == MemoryScope.ENTITY && !scopeId.equals(memory.getScopeId())) return false;
                    }
                    if (memoryTypes != null && !memoryTypes.isEmpty() && !memoryTypes.contains(memory
                            .getMemoryType())) {
                        return false;
                    }
                    if (topics != null && !topics.isEmpty()) {
                        if (memory.getTopics() == null || memory.getTopics().stream().noneMatch(topics::contains)) {
                            return false;
                        }
                    }
                    if (minReusabilityScore > 0 && memory.getReusabilityScore() < minReusabilityScore) {
                        return false;
                    }
                    return true;
                })
                .sorted((a, b) -> {
                    if (queryVector == null) {
                        final var t1 = a.getMemory().getUpdatedAt();
                        final var t2 = b.getMemory().getUpdatedAt();
                        if (t1 == null || t2 == null) {
                            return t1 == null ? (t2 == null ? 0 : 1) : -1;
                        }
                        return t2.compareTo(t1);
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
        final var memoryToSave = agentMemory.toBuilder()
                .createdAt(agentMemory.getCreatedAt() == null ? now : agentMemory.getCreatedAt())
                .updatedAt(now)
                .build();
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
            final var memoryFile = memoryDir.resolve("memory.json");
            final var vectorFile = memoryDir.resolve("vector.json");

            FileUtils.write(memoryFile, mapper.writeValueAsBytes(memoryToSave), false);
            FileUtils.write(vectorFile, mapper.writeValueAsBytes(vector), false);

            cache.put(id, stored);
            return Optional.of(memoryToSave);
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    private double cosineSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null || vectorA.length != vectorB.length) return 0.0;
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @SneakyThrows
    private void loadMemories() {
        try (final var paths = Files.list(memoryRoot)) {
            paths.filter(Files::isDirectory).forEach(path -> {
                try {
                    final var memoryFile = path.resolve("memory.json");
                    final var vectorFile = path.resolve("vector.json");
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
