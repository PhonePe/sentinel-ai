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

package com.phonepe.sentinelai.agentmemory;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 *
 */
public interface AgentMemoryStore {
    List<AgentMemory> findMemories(String scopeId, MemoryScope scope, Set<MemoryType> memoryTypes, List<String> topics,
            String query, int minReusabilityScore, int count);

    default List<AgentMemory> findMemoriesAboutUser(String userId, String query, int count) {
        return findMemories(userId, MemoryScope.ENTITY, EnumSet.of(MemoryType.SEMANTIC), List.of(), query, 0, count);
    }

    default List<AgentMemory> findProcessMemory(String query) {
        return findMemories(null, MemoryScope.AGENT, EnumSet.of(MemoryType.PROCEDURAL), List.of(), query, 0, 10);
    }

    Optional<AgentMemory> save(AgentMemory agentMemory);

}
