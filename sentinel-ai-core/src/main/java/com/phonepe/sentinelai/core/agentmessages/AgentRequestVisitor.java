package com.phonepe.sentinelai.core.agentmessages;

import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;

/**
 *
 */
public interface AgentRequestVisitor<T> {
    T visit(SystemPrompt systemPrompt);

    T visit(UserPrompt userPrompt);

    T visit(ToolCallResponse toolCallResponse);
}
