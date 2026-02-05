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

package com.phonepe.sentinelai.core.agentmessages.responses;

import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.AgentResponse;
import com.phonepe.sentinelai.core.agentmessages.AgentResponseVisitor;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Text response from LLM
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Text extends AgentResponse {
    /**
     * Text content
     */
    String content;

    public Text(String sessionId, String runId, @NonNull String content) {
        this(sessionId, runId, null, null, content);
    }

    @Builder
    @Jacksonized
    public Text(String sessionId, String runId, String messageId, Long timestamp, @NonNull String content) {
        super(AgentMessageType.TEXT_RESPONSE_MESSAGE, sessionId, runId, messageId, timestamp);
        this.content = content;
    }

    @Override
    public <T> T accept(AgentResponseVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
