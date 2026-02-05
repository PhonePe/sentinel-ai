package com.phonepe.sentinelai.storage.session;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Document model for storing {@link com.phonepe.sentinelai.agentmemory.AgentMemory} in ElasticSearch
 */
@Value
@FieldNameConstants
@Builder
public class ESSessionDocument {
    String sessionId;

    String summary;

    List<String> topics;

    String lastSummarizedMessageId;

    long updatedAtMicro;

    LocalDateTime createdAt;

    LocalDateTime updatedAt;
}
