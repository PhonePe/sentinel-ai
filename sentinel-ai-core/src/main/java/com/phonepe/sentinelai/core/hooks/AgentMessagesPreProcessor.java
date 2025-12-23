package com.phonepe.sentinelai.core.hooks;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.List;

/**
 * A pre-processor of agent messages before the messages are sent to an LLM.
 *
 * The pre-processor can be used to modify the list of messages that are sent to a model. Common use cases that can
 * leverage this hook are compaction, inspection etc.
 */
@FunctionalInterface
public interface AgentMessagesPreProcessor {
    AgentMessagesPreProcessResult process(AgentMessagesPreProcessContext ctx,
                                          List<AgentMessage> allMessages,
                                          List<AgentMessage> newMessages);
}
