package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.tools.ExecutableTool;

import java.util.Map;

/**
 *
 */
@FunctionalInterface
public interface ToolRunner<S> {
    ToolCallResponse runTool(AgentRunContext<S> context, Map<String, ExecutableTool> tool, ToolCall toolCall);
}
