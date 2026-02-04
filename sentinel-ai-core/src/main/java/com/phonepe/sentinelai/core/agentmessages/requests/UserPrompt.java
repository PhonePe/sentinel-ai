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
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * User prompt/request sent from user to LLM
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UserPrompt extends AgentRequest {
    String content;
    LocalDateTime sentAt;

    public UserPrompt(
            String sessionId,
            String runId,
            @NonNull String content,
            LocalDateTime sentAt) {
        this(sessionId,
             runId,
             null,
             null,
             content,
             sentAt);
    }

    @Builder
    @Jacksonized
    public UserPrompt(
            String sessionId,
            String runId,
            String messageId,
            Long timestamp,
            @NonNull String content,
            LocalDateTime sentAt) {
        super(AgentMessageType.USER_PROMPT_REQUEST_MESSAGE, sessionId, runId, messageId, timestamp);
        this.content = content;
        this.sentAt = Objects.requireNonNullElse(sentAt, LocalDateTime.now());
    }

    @Override
    public <T> T accept(AgentRequestVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
