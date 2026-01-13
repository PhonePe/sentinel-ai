package com.phonepe.sentinel.session.history.selectors;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;

import java.util.List;
import java.util.Set;

/**
 * Removes all tool call related messages from the session history.
 */
public class RemoveAllToolCallsSelector implements MessageSelector {
    private static final Set<AgentMessageType> TOOL_CALL_TYPES = Set.of(
            AgentMessageType.TOOL_CALL_REQUEST_MESSAGE,
            AgentMessageType.TOOL_CALL_RESPONSE_MESSAGE
                                                                       );

    @Override
    public List<AgentMessage> select(String sessionId, List<AgentMessage> messages) {
        return messages.stream()
                .filter(message -> !TOOL_CALL_TYPES.contains(message.getMessageType()))
                .toList();
    }
}
