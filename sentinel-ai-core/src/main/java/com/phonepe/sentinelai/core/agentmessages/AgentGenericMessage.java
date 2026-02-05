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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Base class for all requests sent by the agent to the LLM
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class AgentGenericMessage extends AgentMessage {

    public enum Role {
        SYSTEM, // System message
        USER,   // User message
        ASSISTANT, // Assistant message
        TOOL_CALL // Tool call message
    }

    private final Role role;

    protected AgentGenericMessage(String sessionId, String runId, String messageId, Long timestamp,
            AgentMessageType messageType, Role role) {
        super(messageType, sessionId, runId, messageId, timestamp);
        this.role = role;
    }

    public abstract <T> T accept(AgentGenericMessageVisitor<T> visitor);

    @Override
    public <T> T accept(AgentMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
