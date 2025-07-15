package com.phonepe.sentinelai.core.agentmessages.requests;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

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
            Role role,
            ResourceType resourceType,
            String uri,
            String mimeType,
            String content,
            String serializedJson) {
        super(AgentMessageType.GENERIC_RESOURCE_MESSAGE, role);
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
