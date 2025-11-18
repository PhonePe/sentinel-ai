package com.phonepe.sentinelai.core.hooks;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AgentMessagesPreProcessResult {
    List<AgentMessage> transformedMessages;
}
