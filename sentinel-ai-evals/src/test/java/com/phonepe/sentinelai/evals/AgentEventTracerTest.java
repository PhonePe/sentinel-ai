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

package com.phonepe.sentinelai.evals;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.events.EventType;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;

import lombok.val;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


class AgentEventTracerTest {

    @Test
    void testEventCounting() {
        val tracer = new AgentEventTracer();
        tracer.handleAgentEvent(
                                new OutputGeneratedAgentEvent("test-agent",
                                                              "run-1",
                                                              null,
                                                              null,
                                                              "content",
                                                              null,
                                                              Duration.ofMillis(100)));
        tracer.handleAgentEvent(
                                new OutputGeneratedAgentEvent("test-agent",
                                                              "run-2",
                                                              null,
                                                              null,
                                                              "content",
                                                              null,
                                                              Duration.ofMillis(200)));
        tracer.handleAgentEvent(new ToolCallCompletedAgentEvent("test-agent",
                                                                "run-1",
                                                                null,
                                                                null,
                                                                "tc-1",
                                                                "fetch_user",
                                                                null,
                                                                "result",
                                                                Duration.ofMillis(50)));

        assertEquals(2, tracer.getEventCount("test-agent", EventType.OUTPUT_GENERATED, null));
        assertEquals(1, tracer.getEventCount("test-agent", EventType.TOOL_CALL_COMPLETED, "fetch_user"));
        assertEquals(0, tracer.getEventCount("other-agent", EventType.OUTPUT_GENERATED, null));
    }

    @Test
    void testFilterByAgentName() {
        val tracer = new AgentEventTracer();
        tracer.handleAgentEvent(
                                new OutputGeneratedAgentEvent("agent-a",
                                                              "run-1",
                                                              null,
                                                              null,
                                                              "content",
                                                              null,
                                                              Duration.ofMillis(100)));
        tracer.handleAgentEvent(
                                new OutputGeneratedAgentEvent("agent-b",
                                                              "run-2",
                                                              null,
                                                              null,
                                                              "content",
                                                              null,
                                                              Duration.ofMillis(200)));

        assertEquals(1, tracer.getEventCount("agent-a", EventType.OUTPUT_GENERATED, null));
        assertEquals(2, tracer.getEventCount(null, EventType.OUTPUT_GENERATED, null));
    }

    @Test
    void testLatencyCalculation() {
        val tracer = new AgentEventTracer();
        tracer.handleAgentEvent(
                                new OutputGeneratedAgentEvent("test-agent",
                                                              "run-1",
                                                              null,
                                                              null,
                                                              "content",
                                                              null,
                                                              Duration.ofMillis(100)));
        tracer.handleAgentEvent(
                                new OutputGeneratedAgentEvent("test-agent",
                                                              "run-2",
                                                              null,
                                                              null,
                                                              "content",
                                                              null,
                                                              Duration.ofMillis(200)));

        assertEquals(150.0, tracer.getAverageLatencyMs("test-agent", EventType.OUTPUT_GENERATED, null), 0.01);
    }

    @Test
    void testReset() {
        val tracer = new AgentEventTracer();
        tracer.handleAgentEvent(
                                new OutputGeneratedAgentEvent("test-agent",
                                                              "run-1",
                                                              null,
                                                              null,
                                                              "content",
                                                              null,
                                                              Duration.ofMillis(100)));
        assertEquals(1, tracer.getEventCount("test-agent", EventType.OUTPUT_GENERATED, null));

        tracer.reset();
        assertEquals(0, tracer.getEventCount("test-agent", EventType.OUTPUT_GENERATED, null));
        assertEquals(0.0, tracer.getAverageLatencyMs("test-agent", EventType.OUTPUT_GENERATED, null), 0.01);
    }

    @Test
    void testToolCallLatencyFallback() {
        val tracer = new AgentEventTracer();
        tracer.handleAgentEvent(new ToolCalledAgentEvent("test-agent",
                                                         "run-1",
                                                         null,
                                                         null,
                                                         "tc-1",
                                                         "fetch_user",
                                                         "{}"));
        tracer.handleAgentEvent(new ToolCallCompletedAgentEvent("test-agent",
                                                                "run-1",
                                                                null,
                                                                null,
                                                                "tc-1",
                                                                "fetch_user",
                                                                null,
                                                                "result",
                                                                Duration.ofMillis(50)));

        assertTrue(tracer.getAverageLatencyMs("test-agent", EventType.TOOL_CALL_COMPLETED, "fetch_user") >= 0);
    }
}
