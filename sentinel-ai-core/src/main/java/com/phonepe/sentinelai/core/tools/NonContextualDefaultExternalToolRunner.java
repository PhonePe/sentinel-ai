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

package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import lombok.SneakyThrows;
import org.apache.commons.lang3.NotImplementedException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * This is a trivial implementation of a tool runner that can be used in sampling, async etc paths to use function
 * calling for output generation.
 */
public class NonContextualDefaultExternalToolRunner implements ToolRunner {
    private final String sessionId;
    private final String runId;
    private final ObjectMapper mapper;

    public NonContextualDefaultExternalToolRunner(
            String sessionId,
            String runId,
            ObjectMapper mapper) {
        this.sessionId = sessionId;
        this.runId = runId;
        this.mapper = mapper;
    }

    @Override
    public ToolCallResponse runTool(Map<String, ExecutableTool> tools, ToolCall toolCall) {
        final var tool = Objects.requireNonNull(tools.get(toolCall.getToolName()));
        return tool.accept(new ExecutableToolVisitor<ToolCallResponse>() {
            @Override
            @SneakyThrows
            public ToolCallResponse visit(ExternalTool externalTool) {
                final var response =
                        externalTool.getCallable()
                                .apply(null,
                                       toolCall.getToolCallId(),
                                       toolCall.getArguments());
                return new ToolCallResponse(
                        sessionId,
                        runId,
                        toolCall.getToolCallId(),
                        toolCall.getToolName(),
                        ErrorType.SUCCESS,
                        mapper.writeValueAsString(response.response()),
                        LocalDateTime.now());
            }

            @Override
            public ToolCallResponse visit(InternalTool internalTool) {
                throw new NotImplementedException();
            }
        });
    }
}
