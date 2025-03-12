package com.phonepe.sentinelai.core.agentmemory;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 *
 */
public interface AgentMemoryStore {
    List<AgentMemory> findMemories(
            String scopeId,
            MemoryScope scope,
            Set<MemoryType> memoryTypes,
            String query,
            List<String> topics,
            int count);

    default List<AgentMemory> findMemoriesAboutUser(
            String userId,
            String query,
            List<String> topics,
            int count) {
        return findMemories(userId, MemoryScope.ENTITY, EnumSet.of(MemoryType.SEMANTIC), query, topics, count);
    }

    default Optional<AgentMemory> saveMemoryAboutUser(String userId, String content, List<String> topics) {
        return createOrUpdate(AgentMemory.builder()
                                      .memoryType(MemoryType.SEMANTIC)
                                      .scope(MemoryScope.ENTITY)
                                      .scopeId(userId)
                                      .content(content)
                                      .topics(topics)
                                      .build());
    }

    default Optional<AgentMemory> sessionSummary(String sessionId) {
        return findMemories(sessionId,
                            MemoryScope.SESSION,
                            Set.of(MemoryType.EPISODIC),
                            null,
                            null,
                            1)
                .stream()
                .findAny();
    }

    default Optional<AgentMemory> updateSessionSummary(String sessionId, String content, List<String> topics) {
        return createOrUpdate(AgentMemory.builder()
                                      .memoryType(MemoryType.EPISODIC)
                                      .scope(MemoryScope.SESSION)
                                      .scopeId(sessionId)
                                      .content(content)
                                      .topics(topics)
                                      .build());
    }

    Optional<AgentMemory> createOrUpdate(AgentMemory agentMemory);

    Optional<AgentMemory> updateMemory(MemoryScope scope, String scopeId, String name,
                                       UnaryOperator<AgentMemory> updater);
}
