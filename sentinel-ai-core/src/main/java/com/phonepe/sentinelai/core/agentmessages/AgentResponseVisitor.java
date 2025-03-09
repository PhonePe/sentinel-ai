package com.phonepe.sentinelai.core.agentmessages;

import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;

/**
 * Handle specific response types
 */
public interface AgentResponseVisitor<T> {
    T visit(final Text text);

    T visit(StructuredOutput structuredOutput);

    T visit(ToolCall toolCall);
}
