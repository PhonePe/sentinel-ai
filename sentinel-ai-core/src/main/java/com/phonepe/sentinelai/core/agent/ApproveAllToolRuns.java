package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.tools.ToolRunApprovalSeeker;

/**
 * Marks all tool calls as approved.
 */
final class ApproveAllToolRuns<R,T,A extends Agent<R,T,A>> implements ToolRunApprovalSeeker<R, T, A> {
    @Override
    public boolean seekApproval(A agent, AgentRunContext<R> runContext, ToolCall toolCall) {
        return true;
    }
}
