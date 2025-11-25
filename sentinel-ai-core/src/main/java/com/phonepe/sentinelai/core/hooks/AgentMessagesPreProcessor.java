package com.phonepe.sentinelai.core.hooks;

public interface AgentMessagesPreProcessor {
    AgentMessagesPreProcessResult execute(AgentMessagesPreProcessContext ctx);
}
