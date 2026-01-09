package com.phonepe.sentinel.session.history.modifiers;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Removes all system prompt messages from the agent message history.
 */
public class SystemPromptRemovalModifier<R> implements BiFunction<AgentRunContext<R>, List<AgentMessage>, List<AgentMessage>> {
    @Override
    public List<AgentMessage> apply(AgentRunContext<R> context, List<AgentMessage> agentMessages) {
        return agentMessages.stream()
                .filter(message -> message.getMessageType() != AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE)
                .toList();
    }
}
