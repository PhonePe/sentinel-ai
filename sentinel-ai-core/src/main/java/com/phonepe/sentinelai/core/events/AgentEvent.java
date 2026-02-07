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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An event that has occurred in the agent.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(name = EventType.Values.MESSAGE_RECEIVED, value = MessageReceivedAgentEvent.class),
        @JsonSubTypes.Type(name = EventType.Values.MESSAGE_SENT, value = MessageSentAgentEvent.class),
        @JsonSubTypes.Type(name = EventType.Values.TOOL_CALL_APPROVAL_DENIED, value = ToolCallApprovalDeniedAgentEvent.class),
        @JsonSubTypes.Type(name = EventType.Values.TOOL_CALLED, value = ToolCalledAgentEvent.class),
        @JsonSubTypes.Type(name = EventType.Values.TOOL_CALL_COMPLETED, value = ToolCallCompletedAgentEvent.class),
        @JsonSubTypes.Type(name = EventType.Values.INPUT_RECEIVED, value = InputReceivedAgentEvent.class),
        @JsonSubTypes.Type(name = EventType.Values.OUTPUT_GENERATED, value = OutputGeneratedAgentEvent.class),
        @JsonSubTypes.Type(name = EventType.Values.OUTPUT_ERROR, value = OutputErrorAgentEvent.class)
})
@Data
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class AgentEvent {
    private final EventType type;
    private final String eventId = UUID.randomUUID().toString();
    private final String agentName;
    private final String runId;
    private final String sessionId;
    private final String userId;
    private final LocalDateTime timestamp = LocalDateTime.now();

    public abstract <T> T accept(final AgentEventVisitor<T> visitor);
}
