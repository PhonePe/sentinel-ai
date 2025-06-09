package com.phonepe.sentinelai.agentmemory;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
public interface AgentMemoryStore {
    List<AgentMemory> findMemories(
            String scopeId,
            MemoryScope scope,
            Set<MemoryType> memoryTypes,
            List<String> topics,
            String query,
            int minReusabilityScore,
            int count);

    default List<AgentMemory> findMemoriesAboutUser(
            String userId,
            String query,
            int count) {
        return findMemories(userId, MemoryScope.ENTITY, EnumSet.of(MemoryType.SEMANTIC), List.of(), query, 0, count);
    }

    default List<AgentMemory> findProcessMemory(String query) {
        return findMemories(null, MemoryScope.AGENT, EnumSet.of(MemoryType.PROCEDURAL), List.of(), query, 0, 10);
    }

    Optional<AgentMemory> save(AgentMemory agentMemory);

}
