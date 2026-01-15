package com.phonepe.sentinel.session.history.modifiers;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.List;

/**
 * This interface is used to implement filters that are applied to the list of agent messages
 * before they are persisted to the {@link com.phonepe.sentinel.session.SessionStore}
 */
@FunctionalInterface
public interface MessagePersistencePreFilter<R> {
    List<AgentMessage> filter(AgentRunContext<R> context, List<AgentMessage> agentMessages);
}
