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