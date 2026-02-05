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

package com.phonepe.sentinelai.core.agentmessages.requests;

import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import com.phonepe.sentinelai.core.errors.ErrorType;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Response for a tool run
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ToolCallResponse extends AgentRequest {

    /**
     * Tool call ID as received from the LLM
     */
    String toolCallId;

    /**
     * Name of the tool that was called
     */
    String toolName;

    /**
     * Serialized response that was sent to model
     */
    String response;

    /**
     * boolean to indicate if the response is a success or failure
     */
    ErrorType errorType;

    /**
     * Call time
     */
    LocalDateTime sentAt;

    @Builder
    @Jacksonized
    public ToolCallResponse(String sessionId,
                            String runId,
                            String messageId,
                            Long timestamp,
                            @NonNull String toolCallId,
                            @NonNull String toolName,
                            ErrorType errorType,
                            @NonNull String response,
                            LocalDateTime sentAt) {
        super(AgentMessageType.TOOL_CALL_RESPONSE_MESSAGE,
              sessionId,
              runId,
              messageId,
              timestamp);
        this.toolCallId = toolCallId;
        this.toolName = toolName;
        this.errorType = errorType;
        this.response = response;
        this.sentAt = Objects.requireNonNullElse(sentAt, LocalDateTime.now());
    }

    public ToolCallResponse(String sessionId,
                            String runId,
                            @NonNull String toolCallId,
                            @NonNull String toolName,
                            ErrorType errorType,
                            @NonNull String response,
                            LocalDateTime sentAt) {
        this(sessionId,
             runId,
             null,
             null,
             toolCallId,
             toolName,
             errorType,
             response,
             sentAt);
    }

    @Override
    public <T> T accept(AgentRequestVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public boolean isSuccess() {
        return errorType.equals(ErrorType.SUCCESS);
    }
}
