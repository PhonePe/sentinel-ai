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
public class OutputGeneratedAgentEvent extends AgentEvent {
    /**
     * Serialized content for structured output
     */
    String content;
    /**
     * Elapsed time taken to generate the final output
     */
    Duration elapsedTime;

    @Builder
    @Jacksonized
    public OutputGeneratedAgentEvent(
            @NonNull String agentName,
            @NonNull String runId,
            String sessionId,
            String userId,
            @NonNull String content,
            @NonNull Duration elapsedTime) {
        super(EventType.OUTPUT_GENERATED, agentName, runId, sessionId, userId);
        this.content = content;
        this.elapsedTime = elapsedTime;
    }

    @Override
    public <T> T accept(AgentEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
