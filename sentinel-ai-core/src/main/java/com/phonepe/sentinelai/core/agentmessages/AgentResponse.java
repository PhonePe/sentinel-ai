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

package com.phonepe.sentinelai.core.agentmessages;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * Responses as received from LLM
 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class AgentResponse extends AgentMessage {

    /**
     * Response id as received from LLM
     */
    @Getter
    private final String responseId;

    protected AgentResponse(AgentMessageType messageType,
                            String sessionId,
                            String runId,
                            String messageId,
                            Long timestamp,
                            String responseId) {
        super(messageType, sessionId, runId, messageId, timestamp);
        this.responseId = responseId;
    }

    @Override
    public <T> T accept(AgentMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }

    public abstract <T> T accept(AgentResponseVisitor<T> visitor);
}
