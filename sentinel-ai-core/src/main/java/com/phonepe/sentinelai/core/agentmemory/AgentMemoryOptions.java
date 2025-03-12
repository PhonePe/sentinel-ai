package com.phonepe.sentinelai.core.agentmemory;

import lombok.Builder;
import lombok.Value;

/**
 *
 */
@Value
@Builder
public class AgentMemoryOptions {
    boolean saveMemoryAfterSessionEnd;
    boolean updateSessionSummary;
    int numMessagesForSummarization;
    AgentMemoryStore memoryStore;

}
