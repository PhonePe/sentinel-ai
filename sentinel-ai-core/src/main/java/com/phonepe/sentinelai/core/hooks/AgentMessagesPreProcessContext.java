package com.phonepe.sentinelai.core.hooks;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AgentMessagesPreProcessContext {
    ModelRunContext modelRunContext;
    ModelUsageStats statsForRun;
    List<AgentMessage> allMessages;
}
