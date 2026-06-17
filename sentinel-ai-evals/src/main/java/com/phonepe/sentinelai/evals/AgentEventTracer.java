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

@Slf4j
public class AgentEventTracer {

    private final Map<String, Long> startMsById = new ConcurrentHashMap<>();
    private final List<AgentEvent> capturedEvents = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, LongAdder> latencySum = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> latencyCount = new ConcurrentHashMap<>();

    public AgentEventTracer() {
    }

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

    public int getEventCount(String agentName, EventType eventType, String eventKey) {
        return getEvents(agentName, eventType, eventKey).size();
    }

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
