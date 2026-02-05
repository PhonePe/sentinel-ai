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

import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;

import lombok.Builder;
import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Maintains pre-processors for agents that can be looked up at runtime.
 */
public class AgentMessagesPreProcessors {
    public static final AgentMessagesPreProcessors NONE = AgentMessagesPreProcessors.builder()
            .messagesPreProcessors(Map.of())
            .build();

    private final Map<String, List<AgentMessagesPreProcessor>> messagesPreProcessors;

    public AgentMessagesPreProcessors() {
        this(new ConcurrentHashMap<>());
    }

    @Builder
    public AgentMessagesPreProcessors(
            @NonNull final Map<String, List<AgentMessagesPreProcessor>> messagesPreProcessors) {
        this.messagesPreProcessors = messagesPreProcessors;
    }

    public static AgentMessagesPreProcessors of(String agentName, final List<AgentMessagesPreProcessor> preProcessors) {
        return new AgentMessagesPreProcessors().add(agentName, preProcessors);
    }

    public AgentMessagesPreProcessors add(final String agentName, final AgentMessagesPreProcessor processor) {
        final var existingProcessors = messagesPreProcessors.computeIfAbsent(agentName,
                k -> new CopyOnWriteArrayList<>());

        existingProcessors.add(processor);
        return this;
    }

    public AgentMessagesPreProcessors add(final String agentName, final List<AgentMessagesPreProcessor> preProcessors) {
        preProcessors.forEach(preProcessor -> add(agentName, preProcessor));
        return this;
    }

    public Optional<List<AgentMessagesPreProcessor>> processorsFor(String agentName) {
        return Optional.ofNullable(messagesPreProcessors.get(agentName));
    }

}
