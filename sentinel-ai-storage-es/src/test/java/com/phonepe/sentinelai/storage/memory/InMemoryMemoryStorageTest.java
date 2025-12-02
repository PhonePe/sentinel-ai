package com.phonepe.sentinelai.storage.memory;

import com.phonepe.sentinelai.agentmemory.AgentMemory;
import com.phonepe.sentinelai.agentmemory.MemoryScope;
import com.phonepe.sentinelai.agentmemory.MemoryType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InMemoryMemoryStorage}
 */
@Slf4j
class InMemoryMemoryStorageTest {

    private InMemoryMemoryStorage memoryStorage;

    @BeforeEach
    void setUp() {
        memoryStorage = new InMemoryMemoryStorage();
    }

    @Test
    void testSaveMemory() {
        // Test saving a memory
        AgentMemory memory = createTestMemory(
                "test-agent",
                MemoryScope.ENTITY,
                "user123",
                MemoryType.SEMANTIC,
                "UserName",
                "User's name is John Doe",
                List.of("personal", "name"),
                8
        );

        Optional<AgentMemory> saved = memoryStorage.save(memory);
        
        assertTrue(saved.isPresent());
        assertEquals(memory, saved.get());
    }

    @Test
    void testFindMemoriesWithValidScopeAndScopeId() {
        // Save test memories
        AgentMemory memory1 = createTestMemory(
                "agent1", MemoryScope.ENTITY, "user123", MemoryType.SEMANTIC,
                "UserName", "John Doe", List.of("personal"), 8
        );
        AgentMemory memory2 = createTestMemory(
                "agent1", MemoryScope.ENTITY, "user123", MemoryType.SEMANTIC,
                "UserAge", "25 years old", List.of("personal"), 7
        );
        AgentMemory memory3 = createTestMemory(
                "agent1", MemoryScope.ENTITY, "user456", MemoryType.SEMANTIC,
                "UserName", "Jane Smith", List.of("personal"), 6
        );

        memoryStorage.save(memory1);
        memoryStorage.save(memory2);
        memoryStorage.save(memory3);

        // Find memories for user123
        List<AgentMemory> memories = memoryStorage.findMemories(
                "user123", MemoryScope.ENTITY, Set.of(MemoryType.SEMANTIC),
                List.of(), null, 0, 10
        );

        assertEquals(2, memories.size());
        assertTrue(memories.contains(memory1));
        assertTrue(memories.contains(memory2));
        assertFalse(memories.contains(memory3));
    }

    @Test
    void testFindMemoriesWithNullScopeAndScopeId() {
        // Save test memories with different scopes
        AgentMemory memory1 = createTestMemory(
                "agent1", MemoryScope.ENTITY, "user123", MemoryType.SEMANTIC,
                "UserName", "John Doe", List.of("personal"), 8
        );
        AgentMemory memory2 = createTestMemory(
                "agent2", MemoryScope.AGENT, "proc-scope", MemoryType.PROCEDURAL,
                "Process", "How to handle requests", List.of("procedure"), 7
        );

        memoryStorage.save(memory1);
        memoryStorage.save(memory2);

        // Find all memories when scope and scopeId are null
        List<AgentMemory> allMemories = memoryStorage.findMemories(
                null, null, Set.of(), List.of(), null, 0, 10
        );

        assertEquals(2, allMemories.size());
        assertTrue(allMemories.contains(memory1));
        assertTrue(allMemories.contains(memory2));
    }

    @Test
    void testFindMemoriesWithNullScope() {
        // Save test memories
        AgentMemory memory = createTestMemory(
                "agent1", MemoryScope.ENTITY, "user123", MemoryType.SEMANTIC,
                "UserName", "John Doe", List.of("personal"), 8
        );
        memoryStorage.save(memory);

        // Find memories with null scope but valid scopeId
        List<AgentMemory> memories = memoryStorage.findMemories(
                "user123", null, Set.of(), List.of(), null, 0, 10
        );

        // Should return all memories since scope is null
        assertEquals(1, memories.size());
        assertTrue(memories.contains(memory));
    }

    @Test
    void testFindMemoriesWithNullScopeId() {
        // Save test memories
        AgentMemory memory = createTestMemory(
                "agent1", MemoryScope.ENTITY, "user123", MemoryType.SEMANTIC,
                "UserName", "John Doe", List.of("personal"), 8
        );
        memoryStorage.save(memory);

        // Find memories with valid scope but null scopeId
        List<AgentMemory> memories = memoryStorage.findMemories(
                null, MemoryScope.ENTITY, Set.of(), List.of(), null, 0, 10
        );

        // Should return all memories since scopeId is null
        assertEquals(1, memories.size());
        assertTrue(memories.contains(memory));
    }

    @Test
    void testFindMemoriesEmptyResult() {
        // Save a memory
        AgentMemory memory = createTestMemory(
                "agent1", MemoryScope.ENTITY, "user123", MemoryType.SEMANTIC,
                "UserName", "John Doe", List.of("personal"), 8
        );
        memoryStorage.save(memory);

        // Try to find memories for non-existent scope/scopeId combination
        List<AgentMemory> memories = memoryStorage.findMemories(
                "nonexistent", MemoryScope.AGENT, Set.of(), List.of(), null, 0, 10
        );

        assertTrue(memories.isEmpty());
    }

    @Test
    void testSaveMultipleMemoriesInSameScope() {
        // Save multiple memories in the same scope
        String scopeId = "user123";
        MemoryScope scope = MemoryScope.ENTITY;

        AgentMemory memory1 = createTestMemory(
                "agent1", scope, scopeId, MemoryType.SEMANTIC,
                "UserName", "John Doe", List.of("personal"), 8
        );
        AgentMemory memory2 = createTestMemory(
                "agent1", scope, scopeId, MemoryType.SEMANTIC,
                "UserAge", "25", List.of("personal"), 7
        );
        AgentMemory memory3 = createTestMemory(
                "agent1", scope, scopeId, MemoryType.EPISODIC,
                "LastLogin", "Logged in yesterday", List.of("activity"), 6
        );

        memoryStorage.save(memory1);
        memoryStorage.save(memory2);
        memoryStorage.save(memory3);

        List<AgentMemory> memories = memoryStorage.findMemories(
                scopeId, scope, Set.of(), List.of(), null, 0, 10
        );

        assertEquals(3, memories.size());
        assertTrue(memories.contains(memory1));
        assertTrue(memories.contains(memory2));
        assertTrue(memories.contains(memory3));
    }

    @Test
    void testConcurrentAccess() throws Exception {
        // Test concurrent access to the memory storage
        int numThreads = 10;
        int memoriesPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    // Save memories
                    for (int j = 0; j < memoriesPerThread; j++) {
                        AgentMemory memory = createTestMemory(
                                "agent" + threadId,
                                MemoryScope.ENTITY,
                                "user" + threadId,
                                MemoryType.SEMANTIC,
                                "Memory" + j,
                                "Content " + j,
                                List.of("test"),
                                5
                        );
                        memoryStorage.save(memory);
                    }

                    // Read memories
                    List<AgentMemory> memories = memoryStorage.findMemories(
                            "user" + threadId, MemoryScope.ENTITY,
                            Set.of(), List.of(), null, 0, 100
                    );
                    assertEquals(memoriesPerThread, memories.size());
                } finally {
                    latch.countDown();
                }
            }, executor);
            futures.add(future);
        }

        // Wait for all threads to complete
        latch.await();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Verify total memories saved
        List<AgentMemory> allMemories = memoryStorage.findMemories(
                null, null, Set.of(), List.of(), null, 0, 1000
        );
        assertEquals(numThreads * memoriesPerThread, allMemories.size());

        executor.shutdown();
    }

    @Test
    void testMemoryOverwriteInSameScope() {
        // Test that memories accumulate in the same scope (no overwrite)
        String scopeId = "user123";
        MemoryScope scope = MemoryScope.ENTITY;

        AgentMemory memory1 = createTestMemory(
                "agent1", scope, scopeId, MemoryType.SEMANTIC,
                "UserName", "John Doe", List.of("personal"), 8
        );
        
        AgentMemory memory2 = createTestMemory(
                "agent1", scope, scopeId, MemoryType.SEMANTIC,
                "UserName", "John Smith", List.of("personal"), 9  // Different content, same name
        );

        memoryStorage.save(memory1);
        memoryStorage.save(memory2);

        List<AgentMemory> memories = memoryStorage.findMemories(
                scopeId, scope, Set.of(), List.of(), null, 0, 10
        );

        // Both memories should be present (no overwrite)
        assertEquals(2, memories.size());
        assertTrue(memories.contains(memory1));
        assertTrue(memories.contains(memory2));
    }

    @Test
    void testDifferentMemoryTypes() {
        // Test with different memory types
        String scopeId = "test-scope";
        MemoryScope scope = MemoryScope.AGENT;

        AgentMemory semanticMemory = createTestMemory(
                "agent1", scope, scopeId, MemoryType.SEMANTIC,
                "Fact", "Important fact", List.of("facts"), 8
        );
        
        AgentMemory proceduralMemory = createTestMemory(
                "agent1", scope, scopeId, MemoryType.PROCEDURAL,
                "Process", "How to do something", List.of("procedure"), 7
        );
        
        AgentMemory episodicMemory = createTestMemory(
                "agent1", scope, scopeId, MemoryType.EPISODIC,
                "Event", "What happened", List.of("events"), 6
        );

        memoryStorage.save(semanticMemory);
        memoryStorage.save(proceduralMemory);
        memoryStorage.save(episodicMemory);

        List<AgentMemory> allMemories = memoryStorage.findMemories(
                scopeId, scope, Set.of(), List.of(), null, 0, 10
        );

        assertEquals(3, allMemories.size());
        assertTrue(allMemories.contains(semanticMemory));
        assertTrue(allMemories.contains(proceduralMemory));
        assertTrue(allMemories.contains(episodicMemory));
    }

    @Test
    void testMemoryParametersAreIgnored() {
        // Test that memory type, topics, query, minReusabilityScore, and count parameters are ignored
        // in the current implementation
        
        AgentMemory memory1 = createTestMemory(
                "agent1", MemoryScope.ENTITY, "user123", MemoryType.SEMANTIC,
                "UserName", "John", List.of("personal"), 8
        );
        AgentMemory memory2 = createTestMemory(
                "agent1", MemoryScope.ENTITY, "user123", MemoryType.PROCEDURAL,
                "Process", "How to", List.of("procedure"), 3  // Low reusability score
        );

        memoryStorage.save(memory1);
        memoryStorage.save(memory2);

        // Find with specific memory types - should still return all memories for the scope
        List<AgentMemory> semanticOnly = memoryStorage.findMemories(
                "user123", MemoryScope.ENTITY, Set.of(MemoryType.SEMANTIC),
                List.of(), null, 5, 1  // min score 5, count 1
        );

        // Current implementation returns all memories regardless of filters
        assertEquals(2, semanticOnly.size());
    }

    @Test
    void testEmptyMemoryStorage() {
        // Test operations on empty storage
        List<AgentMemory> memories = memoryStorage.findMemories(
                "any", MemoryScope.ENTITY, Set.of(), List.of(), null, 0, 10
        );
        assertTrue(memories.isEmpty());

        List<AgentMemory> allMemories = memoryStorage.findMemories(
                null, null, Set.of(), List.of(), null, 0, 10
        );
        assertTrue(allMemories.isEmpty());
    }

    @Test
    void testLargeNumberOfMemories() {
        // Test with a large number of memories
        String scopeId = "load-test";
        MemoryScope scope = MemoryScope.ENTITY;
        int numMemories = 1000;

        List<AgentMemory> savedMemories = new ArrayList<>();
        for (int i = 0; i < numMemories; i++) {
            AgentMemory memory = createTestMemory(
                    "agent1", scope, scopeId, MemoryType.SEMANTIC,
                    "Memory" + i, "Content " + i, List.of("test"), i % 10
            );
            memoryStorage.save(memory);
            savedMemories.add(memory);
        }

        List<AgentMemory> retrievedMemories = memoryStorage.findMemories(
                scopeId, scope, Set.of(), List.of(), null, 0, numMemories + 100
        );

        assertEquals(numMemories, retrievedMemories.size());
        assertTrue(retrievedMemories.containsAll(savedMemories));
    }

    @Test
    void testThreadSafetyWithConcurrentHashMap() {
        // Verify that ConcurrentHashMap behavior is maintained
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        List<CompletableFuture<Void>> futures = IntStream.range(0, 100)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    AgentMemory memory = createTestMemory(
                            "agent" + (i % 5),
                            MemoryScope.ENTITY,
                            "user" + (i % 10),
                            MemoryType.SEMANTIC,
                            "Memory" + i,
                            "Content " + i,
                            List.of("concurrent"),
                            5
                    );
                    memoryStorage.save(memory);
                }, executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Verify all memories were saved
        List<AgentMemory> allMemories = memoryStorage.findMemories(
                null, null, Set.of(), List.of(), null, 0, 1000
        );
        assertEquals(100, allMemories.size());

        executor.shutdown();
    }

    @Test
    void testKeyRecordEquality() {
        // Test that the internal Key record works correctly
        String scopeId = "test";
        MemoryScope scope = MemoryScope.ENTITY;

        AgentMemory memory1 = createTestMemory(
                "agent1", scope, scopeId, MemoryType.SEMANTIC,
                "Test1", "Content1", List.of("test"), 5
        );
        AgentMemory memory2 = createTestMemory(
                "agent2", scope, scopeId, MemoryType.PROCEDURAL,
                "Test2", "Content2", List.of("test"), 6
        );

        memoryStorage.save(memory1);
        memoryStorage.save(memory2);

        // Both memories should be in the same scope
        List<AgentMemory> memories = memoryStorage.findMemories(
                scopeId, scope, Set.of(), List.of(), null, 0, 10
        );

        assertEquals(2, memories.size());
        assertTrue(memories.contains(memory1));
        assertTrue(memories.contains(memory2));
    }

    /**
     * Helper method to create test AgentMemory instances
     */
    private AgentMemory createTestMemory(
            String agentName,
            MemoryScope scope,
            String scopeId,
            MemoryType memoryType,
            String name,
            String content,
            List<String> topics,
            int reusabilityScore) {
        
        return AgentMemory.builder()
                .agentName(agentName)
                .scope(scope)
                .scopeId(scopeId)
                .memoryType(memoryType)
                .name(name)
                .content(content)
                .topics(topics)
                .reusabilityScore(reusabilityScore)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
