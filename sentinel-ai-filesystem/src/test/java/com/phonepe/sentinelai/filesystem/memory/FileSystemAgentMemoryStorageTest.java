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
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class FileSystemAgentMemoryStorageTest {

    @TempDir
    Path tempDir;

    private EmbeddingModel embeddingModel;
    private FileSystemAgentMemoryStorage memoryStorage;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonUtils.createMapper();
        embeddingModel = Mockito.mock(EmbeddingModel.class);
        // Default mock behavior
        when(embeddingModel.getEmbedding(anyString())).thenReturn(new float[]{
                0.1f, 0.2f, 0.3f
        });

        memoryStorage = new FileSystemAgentMemoryStorage(tempDir.toString(), objectMapper, embeddingModel);
    }

    @Test
    void testFiltering() {
        // Save multiple memories with different scopes and types
        saveMemory("m1", MemoryScope.AGENT, "agent1", MemoryType.SEMANTIC, List.of("t1"), 5);
        saveMemory("m2", MemoryScope.ENTITY, "entity1", MemoryType.EPISODIC, List.of("t2"), 8);
        saveMemory("m3", MemoryScope.AGENT, "agent1", MemoryType.PROCEDURAL, List.of("t1", "t2"), 3);

        // Filter by scope
        assertEquals(2, memoryStorage.findMemories("agent1", MemoryScope.AGENT, null, null, null, 0, 10).size());
        assertEquals(1, memoryStorage.findMemories("entity1", MemoryScope.ENTITY, null, null, null, 0, 10).size());

        // Filter by type
        assertEquals(1,
                     memoryStorage.findMemories(null, null, Set.of(MemoryType.SEMANTIC), null, null, 0, 10).size());

        // Filter by topics
        assertEquals(2, memoryStorage.findMemories(null, null, null, List.of("t1"), null, 0, 10).size());
        assertEquals(2, memoryStorage.findMemories(null, null, null, List.of("t2"), null, 0, 10).size());
        assertEquals(3,
                     memoryStorage.findMemories(null, null, null, List.of("t1", "t2"), null, 0, 10).size());

        // Filter by reusability score
        assertEquals(2, memoryStorage.findMemories(null, null, null, null, null, 5, 10).size());
    }

    @Test
    void testPersistence() {
        saveMemory("m1", MemoryScope.AGENT, "agent1", MemoryType.SEMANTIC, List.of("t1"), 5);

        // Create a new storage instance pointing to the same directory
        final var newStorage = new FileSystemAgentMemoryStorage(tempDir.toString(), objectMapper, embeddingModel);

        final List<AgentMemory> memories = newStorage.findMemories(null, null, null, null, null, 0, 10);
        assertEquals(1, memories.size());
        assertEquals("m1", memories.get(0).getName());
    }

    @Test
    void testSaveAndFind() {
        final AgentMemory memory = AgentMemory.builder()
                .agentName("test-agent")
                .scope(MemoryScope.AGENT)
                .scopeId("test-agent")
                .memoryType(MemoryType.SEMANTIC)
                .name("test-memory")
                .content("some content")
                .topics(List.of("topic1"))
                .reusabilityScore(5)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        memoryStorage.save(memory);

        final List<AgentMemory> memories = memoryStorage.findMemories("test-agent",
                                                                      MemoryScope.AGENT,
                                                                      null,
                                                                      null,
                                                                      null,
                                                                      0,
                                                                      10);
        assertFalse(memories.isEmpty());
        assertEquals(1, memories.size());
        assertEquals("test-memory", memories.get(0).getName());
    }

    @Test
    void testSemanticSearch() {
        // Mock embeddings for query and memories
        when(embeddingModel.getEmbedding("query")).thenReturn(new float[]{
                1.0f, 0.0f
        });

        // m1 is very similar to query
        saveMemoryWithVector("m1", new float[]{
                1.0f, 0.1f
        });
        // m2 is less similar
        saveMemoryWithVector("m2", new float[]{
                0.1f, 1.0f
        });

        final List<AgentMemory> results = memoryStorage.findMemories(null, null, null, null, "query", 0, 10);
        assertEquals(2, results.size());
        assertEquals("m1", results.get(0).getName());
        assertEquals("m2", results.get(1).getName());
    }

    private AgentMemory saveMemory(String name,
                                   MemoryScope scope,
                                   String scopeId,
                                   MemoryType type,
                                   List<String> topics,
                                   int score) {
        final AgentMemory memory = AgentMemory.builder()
                .agentName("agent1")
                .scope(scope)
                .scopeId(scopeId)
                .memoryType(type)
                .name(name)
                .content("content for " + name)
                .topics(topics)
                .reusabilityScore(score)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        memoryStorage.save(memory);
        return memory;
    }

    private AgentMemory saveMemoryWithVector(String name, float[] vector) {
        final String content = "content for " + name;
        when(embeddingModel.getEmbedding(content)).thenReturn(vector);
        return saveMemory(name, MemoryScope.AGENT, "agent1", MemoryType.SEMANTIC, null, 0);
    }
}
