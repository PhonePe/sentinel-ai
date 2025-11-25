package com.phonepe.sentinelai.core.hooks;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AgentMessagesPreProcessContext {
    ModelRunContext modelRunContext;
    List<AgentMessage> allMessages;
}
