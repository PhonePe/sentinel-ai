package com.phonepe.sentinelai.configuredagents;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

/**
 * Captures the end state of an agentic tool execution.
 *
 * The structured response guides the LLM whether the execution was successful or not
 * and to make further decisioning based on the error/agentOutput.
 */
@Builder
@Value
public class AgentExecutionResult {
    boolean successful;
    JsonNode error;
    JsonNode agentOutput;

    public static AgentExecutionResult success(JsonNode agentOutput) {
        return AgentExecutionResult.builder()
                .successful(true)
                .agentOutput(agentOutput)
                .build();
    }

    public static AgentExecutionResult fail(JsonNode error) {
        return AgentExecutionResult.builder()
                .successful(false)
                .error(error)
                .build();
    }
}
