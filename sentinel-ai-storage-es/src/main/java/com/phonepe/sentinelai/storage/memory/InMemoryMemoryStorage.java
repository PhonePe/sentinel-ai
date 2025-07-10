package com.phonepe.sentinelai.storage.memory;

import com.phonepe.sentinelai.agentmemory.AgentMemory;
import com.phonepe.sentinelai.agentmemory.AgentMemoryStore;
import com.phonepe.sentinelai.agentmemory.MemoryScope;
import com.phonepe.sentinelai.agentmemory.MemoryType;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class InMemoryMemoryStorage implements AgentMemoryStore {

    private record Key(MemoryScope scope, String scopeId) {
    }

    private final Map<Key, List<AgentMemory>> memories = new ConcurrentHashMap<>();

    @Override
    public List<AgentMemory> findMemories(String scopeId, MemoryScope scope, Set<MemoryType> memoryTypes, List<String> topics, String query, int minReusabilityScore, int count) {
        if (scopeId == null || scope == null) {
            return memories.values().stream()
                    .flatMap(List::stream)
                    .toList();
        } else {
            return memories.getOrDefault(new Key(scope, scopeId), List.of());
        }
    }

    @Override
    public Optional<AgentMemory> save(AgentMemory agentMemory) {
        final var key = new Key(agentMemory.getScope(), agentMemory.getScopeId());
        final var memsInScope = memories.computeIfAbsent(key, k -> new ArrayList<>());
        memsInScope.add(agentMemory);
        return Optional.of(agentMemory);
    }
}
