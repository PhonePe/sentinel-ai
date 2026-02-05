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

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AgentMessagesPreProcessors}.
 */
class AgentMessagesPreProcessorsTest {

    private static final AgentMessagesPreProcessor MOCK_PROCESSOR = (ctx, a, b) -> null;
    private static final AgentMessagesPreProcessor ANOTHER_PROCESSOR = (ctx, a, b) -> null;

    @Test
    void testAddEmptyProcessorsList() {
        final var preprocessors = new AgentMessagesPreProcessors();
        final var agentName = "agent1";

        preprocessors.add(agentName, List.of());

        assertTrue(preprocessors.processorsFor(agentName).isEmpty());
    }

    @Test
    void testAddMultipleProcessorsForSameAgent() {
        final var agentName = "agent1";
        final var preprocessors = AgentMessagesPreProcessors.of(agentName, List.of(MOCK_PROCESSOR))
                .add(agentName, ANOTHER_PROCESSOR);

        assertTrue(preprocessors.processorsFor(agentName).isPresent());
        final var processors = preprocessors.processorsFor(agentName).get();
        assertEquals(2, processors.size());
        assertTrue(processors.contains(MOCK_PROCESSOR));
        assertTrue(processors.contains(ANOTHER_PROCESSOR));
    }

    @Test
    void testAddProcessorsList() {
        final var preprocessors = new AgentMessagesPreProcessors();
        final var agentName = "agent1";
        final var processorsList = List.of(MOCK_PROCESSOR, ANOTHER_PROCESSOR);

        final var result = preprocessors.add(agentName, processorsList);

        assertNotNull(result);
        assertTrue(preprocessors.processorsFor(agentName).isPresent());
        final var processors = preprocessors.processorsFor(agentName).get();
        assertEquals(2, processors.size());
        assertTrue(processors.contains(MOCK_PROCESSOR));
        assertTrue(processors.contains(ANOTHER_PROCESSOR));
    }

    @Test
    void testAddSingleProcessor() {

        final var agentName = "agent1";
        final var preprocessors = AgentMessagesPreProcessors.of(agentName, List.of(MOCK_PROCESSOR));

        assertTrue(preprocessors.processorsFor(agentName).isPresent());
        final var processors = preprocessors.processorsFor(agentName).get();
        assertEquals(1, processors.size());
        assertTrue(processors.contains(MOCK_PROCESSOR));
    }

    @Test
    void testProcessorsForExistingAgent() {

        final var agentName = "agent1";
        final var preprocessors = AgentMessagesPreProcessors.of(agentName, List.of(MOCK_PROCESSOR));

        final var result = preprocessors.processorsFor(agentName);

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals(MOCK_PROCESSOR, result.get().get(0));
    }

    @Test
    void testProcessorsForMultipleDifferentAgents() {
        final var preprocessors = AgentMessagesPreProcessors.of("agent1", List.of(MOCK_PROCESSOR))
                .add("agent2", ANOTHER_PROCESSOR);

        final var agent1Processors = preprocessors.processorsFor("agent1");
        final var agent2Processors = preprocessors.processorsFor("agent2");

        assertTrue(agent1Processors.isPresent());
        assertTrue(agent2Processors.isPresent());
        assertEquals(1, agent1Processors.get().size());
        assertEquals(1, agent2Processors.get().size());
        assertEquals(MOCK_PROCESSOR, agent1Processors.get().get(0));
        assertEquals(ANOTHER_PROCESSOR, agent2Processors.get().get(0));
    }

    @Test
    void testProcessorsForNonExistentAgent() {
        final var preprocessors = AgentMessagesPreProcessors.NONE;
        final var result = preprocessors.processorsFor("non-existent-agent");
        assertTrue(result.isEmpty());
    }
}
