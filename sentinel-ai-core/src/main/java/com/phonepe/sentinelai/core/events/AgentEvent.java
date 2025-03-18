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
        @JsonSubTypes.Type(name = EventType.Values.TOOL_CALLED, value = ToolCalledAgentEvent.class),
        @JsonSubTypes.Type(name = EventType.Values.TOOL_CALL_COMPLETED, value = ToolCallCompletedAgentEvent.class)
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
