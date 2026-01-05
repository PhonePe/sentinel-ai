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
    public AgentMessagesPreProcessors(@NonNull final Map<String, List<AgentMessagesPreProcessor>> messagesPreProcessors) {
        this.messagesPreProcessors = messagesPreProcessors;
    }

    public AgentMessagesPreProcessors add(final String agentName,
                                          final AgentMessagesPreProcessor processor) {
        final var existingProcessors = messagesPreProcessors.computeIfAbsent(
                agentName, k -> new CopyOnWriteArrayList<>());

        existingProcessors.add(processor);
        return this;
    }

    public AgentMessagesPreProcessors add(final String agentName,
                                          final List<AgentMessagesPreProcessor> preProcessors) {
        preProcessors.forEach(
                preProcessor -> add(agentName, preProcessor));
        return this;
    }

    public Optional<List<AgentMessagesPreProcessor>> processorsFor(String agentName) {
        return Optional.ofNullable(
                messagesPreProcessors.get(agentName));
    }

    public static AgentMessagesPreProcessors of(String agentName, final List<AgentMessagesPreProcessor> preProcessors) {
        return new AgentMessagesPreProcessors()
                .add(agentName, preProcessors);
    }

}
