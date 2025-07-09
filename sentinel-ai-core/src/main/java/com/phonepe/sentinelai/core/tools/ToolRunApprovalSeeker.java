package com.phonepe.sentinelai.core.tools;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;

/**
 * Interface for seeking approval for tool runs.
 */
@FunctionalInterface
public interface ToolRunApprovalSeeker<R, T, A extends Agent<R,T, A>> {
    boolean seekApproval(A agent, AgentRunContext<R> runContext, ToolCall toolCall);
}
