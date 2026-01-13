package com.phonepe.sentinelai.core.agentmessages.requests;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class GenericResource extends AgentGenericMessage {
    public enum ResourceType {
        TEXT, BLOB
    }

    ResourceType resourceType;
    String uri;
    String mimeType;

    String content;
    String serializedJson;

    public GenericResource(
            String sessionId,
            String runId,
            Role role,
            ResourceType resourceType,
            String uri,
            String mimeType,
            String content,
            String serializedJson) {
        this(sessionId,
             runId,
             null,
             null,
             role,
             resourceType,
             uri,
             mimeType,
             content,
             serializedJson);
    }

    @Builder
    @Jacksonized
    public GenericResource(
            String sessionId,
            String runId,
            String messageId,
            Long timestamp,
            Role role,
            ResourceType resourceType,
            String uri,
            String mimeType,
            String content,
            String serializedJson) {
        super(sessionId, runId, messageId, timestamp, AgentMessageType.GENERIC_RESOURCE_MESSAGE, role);
        this.resourceType = resourceType;
        this.uri = uri;
        this.mimeType = mimeType;
        this.content = content;
        this.serializedJson = serializedJson;
    }

    @Override
    public <T> T accept(AgentGenericMessageVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
