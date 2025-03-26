package com.phonepe.sentinelai.storage.memory;

import com.phonepe.sentinelai.agentmemory.MemoryScope;
import com.phonepe.sentinelai.agentmemory.MemoryType;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

import java.time.LocalDateTime;
import java.util.List;

/**
 *
 */
@Value
@FieldNameConstants
@Builder
public class ESAgentMemoryDocument {
    String id;

    String agentName;

    MemoryScope scope;

    String scopeId;

    MemoryType memoryType;

    String name;

    String content;

    float[] contentVector;

    List<String> topics;

    int reusabilityScore;

    LocalDateTime createdAt;

    LocalDateTime updatedAt;
}
