package com.phonepe.sentinelai.core.model;

import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests {@link Model}
 */
@SuppressWarnings("java:S5778")
class ModelTest {
    @Test
    void testStreamThrowsNotImplementedException() {
        final var model = new Model() {
            @Override
            public CompletableFuture<ModelOutput> compute(
                    ModelRunContext context,
                    Collection<ModelOutputDefinition> outputDefinitions,
                    List<com.phonepe.sentinelai.core.agentmessages.AgentMessage> oldMessages,
                    Map<String, ExecutableTool> tools,
                    ToolRunner toolRunner,
                    EarlyTerminationStrategy earlyTerminationStrategy,
                    List<AgentMessagesPreProcessor> preProcessors) {
                return null;
            }

        };
        assertThrows(NotImplementedException.class,
                     () -> model.stream(null, List.of(), List.of(), Map.of(), null, null, bytes -> {}, List.of()));
    }

    @Test
    void testStreamTextThrowsNotImplementedException() {
        final var model = new Model() {
            @Override
            public CompletableFuture<ModelOutput> compute(
                    ModelRunContext context,
                    Collection<ModelOutputDefinition> outputDefinitions,
                    List<com.phonepe.sentinelai.core.agentmessages.AgentMessage> oldMessages,
                    Map<String, ExecutableTool> tools,
                    ToolRunner toolRunner,
                    EarlyTerminationStrategy earlyTerminationStrategy,
                    List<AgentMessagesPreProcessor> preProcessors) {
                return null;
            }

        };
        assertThrows(NotImplementedException.class,
                     () -> model.streamText(null, List.of(), Map.of(), null, null, bytes -> {}, List.of()));
    }
}