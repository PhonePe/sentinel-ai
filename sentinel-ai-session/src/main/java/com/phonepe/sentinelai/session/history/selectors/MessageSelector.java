package com.phonepe.sentinelai.session.history.selectors;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.List;

/**
 *
 */
@FunctionalInterface
public interface MessageSelector  {
    List<AgentMessage> select(String sessionId, List<AgentMessage> messages);
}
