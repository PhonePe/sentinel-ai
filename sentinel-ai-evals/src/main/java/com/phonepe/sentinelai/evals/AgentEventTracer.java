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

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.events.AgentEvent;
import com.phonepe.sentinelai.core.events.EventType;
import com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCallCompletedAgentEvent;
import com.phonepe.sentinelai.core.events.ToolCalledAgentEvent;

import lombok.val;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

/**
 * Captures {@link AgentEvent}s emitted during agent execution for use by eval expectations and metrics.
 *
 * <p>Wire to an agent by passing it to the constructor, or call {@link #handleAgentEvent} directly.
 */
@Slf4j
public class AgentEventTracer {

    private final Map<String, Long> startMsById = new ConcurrentHashMap<>();
    private final List<AgentEvent> capturedEvents = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, LongAdder> latencySum = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> latencyCount = new ConcurrentHashMap<>();

    public AgentEventTracer() {
    }

    /**
     * Wires this tracer to the supplied agent's event bus.
     *
     * @param agent agent whose events should be captured
     * @param <T>   request type accepted by the agent
     * @param <R>   response type produced by the agent
     */
    public <T, R> AgentEventTracer(Agent<T, R, ?> agent) {
        val eventBus = agent.getSetup().getEventBus();
        eventBus.onEvent().connect(this::handleAgentEvent);
        log.debug("AgentEventTracer wired to agent '{}' event bus", agent.name());
    }

    private static String buildKey(String agentName, EventType eventType, String eventKey) {
        return (agentName != null ? agentName : "")
                + "#" + eventType.name()
                + "#" + (eventKey != null ? eventKey : "");
    }

    private static boolean matchesEventKey(AgentEvent event, String eventKey) {
        if (eventKey == null || eventKey.isEmpty()) {
            return true;
        }
        if (event instanceof ToolCallCompletedAgentEvent e) {
            return eventKey.equals(e.getToolCallName());
        }
        if (event instanceof ToolCalledAgentEvent e) {
            return eventKey.equals(e.getToolCallName());
        }
        return false;
    }

    /**
     * Returns all captured events matching the supplied predicate.
     *
     * @param predicate filter to apply
     * @return list of matching events (possibly empty)
     */
    public List<AgentEvent> findEvents(Predicate<AgentEvent> predicate) {
        synchronized (capturedEvents) {
            val result = new ArrayList<AgentEvent>();
            for (val event : capturedEvents) {
                if (predicate.test(event)) {
                    result.add(event);
                }
            }
            return result;
        }
    }

    /**
     * Returns the average latency in milliseconds for the given event key.
     *
     * @param agentName agent to match; {@code null} or empty aggregates across all agents
     * @param eventType event type to measure
     * @param eventKey  event-specific key (e.g. tool name); {@code null} ignores the key
     * @return average latency in ms, or {@code 0.0} when no samples exist
     */
    public double getAverageLatencyMs(String agentName, EventType eventType, String eventKey) {
        if (agentName != null && !agentName.isEmpty()) {
            val key = buildKey(agentName, eventType, eventKey);
            val count = getLatencyCount(key);
            return count == 0 ? 0.0 : (double) getLatencySum(key) / count;
        }
        long totalSum = 0;
        long totalCount = 0;
        val suffix = "#" + eventType.name() + "#" + (eventKey != null ? eventKey : "");
        for (val entry : latencySum.entrySet()) {
            if (entry.getKey().endsWith(suffix)) {
                totalSum += entry.getValue().sum();
                val countAdder = latencyCount.get(entry.getKey());
                if (countAdder != null) {
                    totalCount += countAdder.sum();
                }
            }
        }
        return totalCount == 0 ? 0.0 : (double) totalSum / totalCount;
    }

    /**
     * Returns the number of captured events matching the given filters.
     *
     * @param agentName agent to match; {@code null} or empty ignores the agent name
     * @param eventType event type to count
     * @param eventKey  event-specific key; {@code null} or empty ignores the key
     * @return count of matching events
     */
    public int getEventCount(String agentName, EventType eventType, String eventKey) {
        return getEvents(agentName, eventType, eventKey).size();
    }

    /**
     * Returns all captured events matching the given filters.
     *
     * @param agentName agent to match; {@code null} or empty ignores the agent name
     * @param eventType event type to return
     * @param eventKey  event-specific key; {@code null} or empty ignores the key
     * @return list of matching events (possibly empty)
     */
    public List<AgentEvent> getEvents(String agentName, EventType eventType, String eventKey) {
        return findEvents(e -> {
            if (agentName != null && !agentName.isEmpty() && !agentName.equals(e.getAgentName())) {
                return false;
            }
            if (e.getType() != eventType) {
                return false;
            }
            if (eventKey == null || eventKey.isEmpty()) {
                return true;
            }
            return matchesEventKey(e, eventKey);
        });
    }

    public void handleAgentEvent(AgentEvent event) {
        capturedEvents.add(event);
        switch (event.getType()) {
            case OUTPUT_GENERATED -> {
                val elapsed = ((OutputGeneratedAgentEvent) event).getElapsedTime();
                if (elapsed != null) {
                    recordLatency(
                                  event.getAgentName(),
                                  EventType.OUTPUT_GENERATED,
                                  null,
                                  elapsed.toMillis());
                }
            }
            case TOOL_CALLED -> startMsById.put(
                                                ((ToolCalledAgentEvent) event).getToolCallId(),
                                                System.currentTimeMillis());
            case TOOL_CALL_COMPLETED -> {
                final long latencyMs;
                val elapsed = ((ToolCallCompletedAgentEvent) event).getElapsedTime();
                if (elapsed != null) {
                    latencyMs = elapsed.toMillis();
                }
                else {
                    val startMs = startMsById.remove(((ToolCallCompletedAgentEvent) event).getToolCallId());
                    latencyMs = startMs != null
                            ? System.currentTimeMillis() - startMs
                            : 0;
                }
                if (latencyMs > 0) {
                    val toolName = ((ToolCallCompletedAgentEvent) event).getToolCallName();
                    if (toolName != null && !toolName.isEmpty()) {
                        recordLatency(event.getAgentName(), EventType.TOOL_CALL_COMPLETED, toolName, latencyMs);
                    }
                    else {
                        recordLatency(event.getAgentName(), EventType.TOOL_CALL_COMPLETED, null, latencyMs);
                    }
                }
            }
            default -> {
            }
        }
    }

    /**
     * Clears all captured events and latency data.
     */
    public void reset() {
        startMsById.clear();
        latencySum.clear();
        latencyCount.clear();
        capturedEvents.clear();
    }

    private long getLatencyCount(String key) {
        val adder = latencyCount.get(key);
        return adder == null ? 0 : adder.sum();
    }

    private long getLatencySum(String key) {
        val adder = latencySum.get(key);
        return adder == null ? 0 : adder.sum();
    }

    private void recordLatency(String agentName, EventType eventType, String eventKey, long latencyMs) {
        val key = buildKey(agentName, eventType, eventKey);
        latencyCount.computeIfAbsent(key, k -> new LongAdder()).increment();
        latencySum.computeIfAbsent(key, k -> new LongAdder()).add(latencyMs);
    }
}
