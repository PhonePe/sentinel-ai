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

import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;

import lombok.extern.slf4j.Slf4j;

/**
 * Guards against a model repeating the same tool call without making progress.
 *
 * <p>Some models call the same tool again and again with the same name and the same arguments.
 * The framework loop in the model layer has no iteration cap, so such a run continues until it
 * is stopped externally. This guard counts consecutive identical tool calls within one run.
 * When the count reaches the configured maximum, the guard blocks further identical calls and
 * returns an error {@link ToolCallResponse} for the blocked call. The response keeps the
 * tool-call id of the blocked call, so the tool-call id to tool-response pairing required by
 * the OpenAI protocol stays valid. The model receives the error as a normal tool response and
 * can move ahead.
 *
 * <p>The count resets when a tool call with a different tool name or different arguments
 * arrives. A value of {@code maxIdenticalToolCalls <= 0} disables the guard.
 *
 * <p>Thread safety: the model layer may run several tool calls of one round in parallel. The
 * state is guarded with a monitor, so concurrent calls serialize. The count is then exact.
 */
@Slf4j
public class RepeatedToolCallGuard {

    private final int maxIdenticalToolCalls;
    private final String sessionId;
    private final String runId;

    private String lastKey;
    private int count;

    /**
     * @param maxIdenticalToolCalls Maximum number of consecutive identical tool calls allowed
     *                              in one run. A value {@code <= 0} disables the guard.
     * @param sessionId             Session id used in the blocked tool responses
     * @param runId                 Run id used in the blocked tool responses
     */
    public RepeatedToolCallGuard(final int maxIdenticalToolCalls,
                                 final String sessionId,
                                 final String runId) {
        this.maxIdenticalToolCalls = maxIdenticalToolCalls;
        this.sessionId = sessionId;
        this.runId = runId;
    }

    /**
     * Records the given tool call and decides if it is a blocked repeat.
     *
     * @param toolCall The tool call that the model wants to run
     * @return A blocking error response when the call repeats a previous call too many times
     *         in a row, or {@code null} when the call is allowed. The caller must not run the
     *         tool when this method returns a response.
     */
    public synchronized ToolCallResponse registerCall(final ToolCall toolCall) {
        if (maxIdenticalToolCalls <= 0) {
            return null; // Guard is disabled
        }
        final var key = toolCall.getToolName() + "\n" + toolCall.getArguments();
        count = key.equals(lastKey) ? count + 1 : 1;
        lastKey = key;
        if (count < maxIdenticalToolCalls) {
            return null;
        }
        final var responseMessage = ("This tool call repeats the previous call to tool '%s' with "
                + "identical arguments. The earlier tool response in this conversation already "
                + "contains the result. Do not call this tool again with the same arguments. "
                + "Read the previous tool response and continue with the next step.")
                .formatted(toolCall.getToolName());
        log.warn("Blocking repeated tool call in run {}: tool '{}' repeated {} times in a row",
                 runId,
                 toolCall.getToolName(),
                 count);
        return ToolCallResponse.builder()
                .sessionId(sessionId)
                .runId(runId)
                .toolCallId(toolCall.getToolCallId())
                .toolName(toolCall.getToolName())
                .response(responseMessage)
                .errorType(ErrorType.TOOL_CALL_PERMANENT_FAILURE)
                .build();
    }
}
