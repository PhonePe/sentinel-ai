package com.phonepe.sentinel.session.history.modifiers;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;

import java.util.List;

/**
 * Removes all system prompt messages from the agent message history.
 */
public class SystemPromptRemovalPreFilter<R> implements MessagePersistencePreFilter<R> {

    @Override
    public List<AgentMessage> filter(AgentRunContext<R> context, List<AgentMessage> agentMessages) {
        return agentMessages.stream()
                .filter(message -> !AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE.equals(message.getMessageType()))
                .toList();
    }
}
