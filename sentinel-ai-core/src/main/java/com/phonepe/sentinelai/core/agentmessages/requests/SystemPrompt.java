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

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * System prompt sent by user
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SystemPrompt extends AgentRequest {
    /**
     * Content of the main system prompt
     */
    String content;

    /**
     * Whether the system prompt is generated dynamically
     */
    boolean dynamic;

    /**
     * Name of the method used to generate the system prompt
     */
    String methodReference;

    @Builder
    @Jacksonized
    public SystemPrompt(String sessionId, String runId, String messageId, Long timestamp, @NonNull String content,
            boolean dynamic, String methodReference) {
        super(AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE, sessionId, runId, messageId, timestamp);
        this.content = content;
        this.dynamic = dynamic;
        this.methodReference = methodReference;
    }

    public SystemPrompt(String sessionId, String runId, @NonNull String content, boolean dynamic,
            String methodReference) {
        this(sessionId, runId, null, null, content, dynamic, methodReference);
    }

    @Override
    public <T> T accept(AgentRequestVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
