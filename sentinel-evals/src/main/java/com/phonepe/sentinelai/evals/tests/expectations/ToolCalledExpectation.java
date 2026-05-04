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

package com.phonepe.sentinelai.evals.tests.expectations;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.MessageExpectation;

import lombok.With;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


public class ToolCalledExpectation<R, T> extends MessageExpectation<R, T> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final String toolName;

    @With
    private int times;

    private Map<String, Object> expectedParams;

    public ToolCalledExpectation(String toolName) {
        this(toolName, 1, null);
    }

    public ToolCalledExpectation(String toolName, int times, Map<String, Object> expectedParams) {
        this.toolName = toolName;
        this.times = times;
        this.expectedParams = expectedParams;
    }

    private static Optional<Map<String, Object>> parseArguments(String arguments) {
        try {
            if (arguments == null || arguments.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(OBJECT_MAPPER.readValue(arguments, MAP_TYPE));
        }
        catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean evaluate(R result,
                            EvalExpectationContext<T> context) {
        if (times < 1) {
            throw new IllegalArgumentException("times must be >= 1");
        }
        final var matchingCalls = context.getOldMessages()
                .stream()
                .filter(message -> Optional.ofNullable(message)
                        .filter(ToolCall.class::isInstance)
                        .map(ToolCall.class::cast)
                        .filter(this::matchesToolCall)
                        .isPresent())
                .collect(Collectors.toList());
        return matchingCalls.size() == times;
    }

    @Override
    public boolean matches(AgentMessage message) {
        return Optional.ofNullable(message)
                .filter(ToolCall.class::isInstance)
                .map(ToolCall.class::cast)
                .filter(this::matchesToolCall)
                .isPresent();
    }

    private boolean matchesToolCall(ToolCall toolCall) {
        if (!toolName.equals(toolCall.getToolName())) {
            return false;
        }
        if (expectedParams == null) {
            return true;
        }
        final var parsedArguments = parseArguments(toolCall.getArguments());
        return parsedArguments.map(expectedParams::equals).orElse(false);
    }
}
