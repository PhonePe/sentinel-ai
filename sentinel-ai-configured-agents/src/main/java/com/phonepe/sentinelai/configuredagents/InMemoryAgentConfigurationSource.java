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

package com.phonepe.sentinelai.configuredagents;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent configuration source that stores agent configurations in memory.
 */
public class InMemoryAgentConfigurationSource implements AgentConfigurationSource {

    private final Map<String, AgentMetadata> agentConfigurations = new ConcurrentHashMap<>();

    @Override
    public List<AgentSearchResponse> find(String query) {
        throw new UnsupportedOperationException("Find operation is not supported in InMemoryAgentConfigurationSource");
    }

    @Override
    public List<AgentMetadata> list() {
        return List.copyOf(agentConfigurations.values());
    }

    @Override
    public Optional<AgentMetadata> read(String agentId) {
        return Optional.ofNullable(agentConfigurations.get(agentId));
    }

    @Override
    public boolean remove(String agentId) {
        return agentConfigurations.remove(agentId) != null;
    }

    @Override
    public Optional<AgentMetadata> save(String agentId, AgentConfiguration agentConfiguration) {
        return Optional.of(agentConfigurations.computeIfAbsent(agentId, id -> new AgentMetadata(agentId,
                agentConfiguration)));
    }


}
