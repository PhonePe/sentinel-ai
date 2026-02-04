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

package com.phonepe.sentinelai.core.events;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

import java.time.Duration;

/**
 * A response was received from the LLM
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MessageReceivedAgentEvent extends AgentEvent {
    AgentMessage message;
    Duration elapsedTime;

    @Builder
    @Jacksonized
    public MessageReceivedAgentEvent(
            @NonNull String agentName,
            @NonNull String runId,
            String sessionId,
            String userId,
            @NonNull AgentMessage message,
            @NonNull Duration elapsedTime) {
        super(EventType.MESSAGE_RECEIVED, agentName, runId, sessionId, userId);
        this.message = message;
        this.elapsedTime = elapsedTime;
    }

    @Override
    public <T> T accept(AgentEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
