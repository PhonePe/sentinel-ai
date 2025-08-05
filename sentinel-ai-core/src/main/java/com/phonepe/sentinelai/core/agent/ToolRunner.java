package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.tools.ExecutableTool;

import java.util.Map;

/**
 *
 */
@FunctionalInterface
public interface ToolRunner {
    ToolCallResponse runTool(Map<String, ExecutableTool> tools, ToolCall toolCall);
}
