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

package com.phonepe.sentinelai.evals.tests.expectations.executors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.expectations.ToolCalledExpectation;

import lombok.val;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Executor for {@link ToolCalledExpectation} – asserts that a named tool was called
 * exactly {@code times} times during agent execution.
 *
 * Extends {@link MessageExpectationExecutor} but overrides {@link #evaluate} to perform
 * count-based matching rather than the default "at least one match" semantics.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public class ToolCalledExpectationExecutor<R, T>
        extends
        MessageExpectationExecutor<ToolCalledExpectation<R, T>, R, T> {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final Agent<R, T, ?> agent;
    private final ObjectMapper objectMapper;

    /**
     * Creates a tool-call executor for the supplied expectation.
     *
     * @param expectation  expectation definition to evaluate
     * @param agent        agent under evaluation, used to resolve declared tool ids
     * @param objectMapper mapper used to parse tool arguments JSON
     */
    public ToolCalledExpectationExecutor(ToolCalledExpectation<R, T> expectation,
                                         Agent<R, T, ?> agent,
                                         ObjectMapper objectMapper) {
        super(expectation);
        this.agent = agent;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    private static Optional<String> getMatching(java.lang.reflect.Method[] methods, String toolName) {
        for (java.lang.reflect.Method method : methods) {
            if ((method.getAnnotation(Tool.class) != null && method.getAnnotation(Tool.class).name().equals(toolName))
                    || method.getName().equals(toolName)) {
                return Optional.of(AgentUtils.id(method.getDeclaringClass().getSimpleName(), method.getName()));
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether the message history contains the expected number of matching tool calls.
     *
     * @param result  agent output (unused)
     * @param context evaluation context containing the message history
     * @return {@code true} when the expected number of matching calls is observed
     */
    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        final int times = expectation.getTimes();
        if (times < 1) {
            throw new IllegalArgumentException("times must be >= 1");
        }

        final var matchingCalls = context.getOldMessages()
                .stream()
                .filter(this::matches)
                .collect(Collectors.toList());

        return matchingCalls.size() == times;
    }

    /**
     * Checks whether a single agent message matches the configured tool-call expectation.
     *
     * @param message message to inspect
     * @return {@code true} when the message is a matching tool call
     */
    @Override
    public boolean matches(AgentMessage message) {
        val rawToolName = expectation.getToolName();
        val toolName = agent == null
                ? rawToolName
                : getMatching(agent.getClass().getDeclaredMethods(), rawToolName).orElse(rawToolName);
        return Optional.ofNullable(message)
                .filter(ToolCall.class::isInstance)
                .map(ToolCall.class::cast)
                .filter(tool -> matchesToolCall(tool, toolName))
                .isPresent();
    }

    private boolean matchesToolCall(ToolCall toolCall, String toolName) {
        if (!toolName.equals(toolCall.getToolName())) {
            return false;
        }
        final var expectedParams = expectation.getExpectedParams();
        if (expectedParams == null) {
            return true;
        }
        return parseArguments(toolCall.getArguments())
                .map(expectedParams::equals)
                .orElse(false);
    }

    private Optional<Map<String, Object>> parseArguments(String arguments) {
        try {
            if (arguments == null || arguments.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(arguments, MAP_TYPE));
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }
}
