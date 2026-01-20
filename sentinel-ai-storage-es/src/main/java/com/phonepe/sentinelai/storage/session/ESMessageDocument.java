package com.phonepe.sentinelai.storage.session;

import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;

/**
 * Representation of an agent message for storage in ElasticSearch
 */
@Value
@FieldNameConstants
@Builder
public class ESMessageDocument {
    AgentMessageType messageType;

    String sessionId;

    String runId;

    String messageId;

    long timestamp;

    String content;

    LocalDateTime createdAt;

    LocalDateTime updatedAt;
}
