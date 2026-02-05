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

import lombok.Value;

import java.util.List;
import java.util.Optional;

/**
 * Source for agent configurations. Stored configurations will be used by the {@link AgentRegistry} to create
 * {@link ConfiguredAgent}s dynamically.
 */
public interface AgentConfigurationSource {
    @Value
    class AgentSearchResponse {
        String agentId;
        AgentConfiguration configuration;
    }

    /**
     * Searches for agents based on a query string.
     *
     * @param query the search query
     * @return a list of {@link AgentSearchResponse} containing agent IDs and their configurations that match the query
     */
    List<AgentSearchResponse> find(final String query);

    /**
     * Lists all the agent configurations for this store.
     *
     * @return a list of {@link AgentMetadata} for all agents
     */
    List<AgentMetadata> list();

    /**
     * Reads the agent configuration for the given agentId.
     *
     * @param agentId the unique identifier for the agent
     * @return the {@link AgentMetadata} if found, or empty if not found
     */
    Optional<AgentMetadata> read(String agentId);

    /**
     * Removes the agent configuration for the given agentId.
     *
     * @param agentId the unique identifier for the agent to be removed
     * @return true if the agent was successfully removed, false otherwise
     */
    boolean remove(String agentId);

    /**
     * Saves the agent configuration.Overwrite behaviour is left for the implementation to decide.
     *
     * @param agentId            the unique identifier for the agent
     * @param agentConfiguration the configuration of the agent to be saved
     * @return an {@link Optional} containing the saved {@link AgentMetadata} if successful, or empty if not
     */
    Optional<AgentMetadata> save(final String agentId, final AgentConfiguration agentConfiguration);
}
