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

package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.utils.ToolUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Slf4j
public class SafeToolRunner implements ToolRunner {

    private final ToolRunner delegate;
    private final AgentSetup agentSetup;
    private final Model model;
    private final String sessionId;
    private final String runId;

    public SafeToolRunner(@NonNull ToolRunner delegate,
                          @NonNull AgentSetup agentSetup,
                          @NonNull Model model,
                          String sessionId,
                          String runId) {
        this.delegate = delegate;
        this.agentSetup = agentSetup;
        this.model = model;
        this.sessionId = sessionId;
        this.runId = runId;
    }

    @Override
    public ToolCallResponse runTool(Map<String, ExecutableTool> tools, ToolCall toolCall) {
        final var response = runToolInternal(tools, toolCall);
        final var maxAllowedToolResponseTokens = agentSetup.getMaxToolResponseTokens() <= 0
                ? AgentSetup.MAX_TOOL_RESPONSE_TOKENS
                : agentSetup.getMaxToolResponseTokens();
        final var responseTokenCount = model.estimateTokenCount(List.<AgentMessage>of(response), agentSetup);
        if (responseTokenCount == Model.TOKEN_COUNT_UNKNOWN) {
            log.warn("Token count estimation is not supported by the current model. "
                    + "Tool response size guard is disabled for tool: {} (callId: {}).",
                     response.getToolName(),
                     response.getToolCallId());
            return response;
        }
        if (responseTokenCount > maxAllowedToolResponseTokens) {
            final var responseMessage = "Tool response too large. Max allowed : %d. Actual: %d tokens. Modify the request to reduce output size."
                    .formatted(maxAllowedToolResponseTokens, responseTokenCount);
            log.warn("Tool response for tool {} (callId: {}) exceeded token limit. Max: {}, Actual: {}. Replacing with error response.",
                     response.getToolName(),
                     response.getToolCallId(),
                     maxAllowedToolResponseTokens,
                     responseTokenCount);
            return ToolCallResponse.builder()
                    .sessionId(sessionId)
                    .runId(runId)
                    .toolCallId(response.getToolCallId())
                    .toolName(response.getToolName())
                    .response(responseMessage)
                    .errorType(ErrorType.TOOL_CALL_PERMANENT_FAILURE)
                    .build();
        }
        return response;
    }

    private ToolCallResponse runToolInternal(Map<String, ExecutableTool> tools, ToolCall toolCall) {
        try {
            return delegate.runTool(tools, toolCall);
        }
        catch (Exception e) {
            return ToolUtils.processUnhandledException(sessionId, runId, toolCall, e);
        }
    }


}
