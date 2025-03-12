package com.phonepe.sentinelai.agentmemory;

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


    Optional<AgentMemory> createOrUpdate(AgentMemory agentMemory);

    Optional<AgentMemory> updateMemory(MemoryScope scope, String scopeId, String name,
                                       UnaryOperator<AgentMemory> updater);
}
