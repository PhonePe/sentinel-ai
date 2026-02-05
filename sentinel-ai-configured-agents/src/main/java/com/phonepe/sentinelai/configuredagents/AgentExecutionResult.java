/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    public static AgentExecutionResult fail(JsonNode error) {
        return AgentExecutionResult.builder().successful(false).error(error).build();
    }

    public static AgentExecutionResult success(JsonNode agentOutput) {
        return AgentExecutionResult.builder().successful(true).agentOutput(agentOutput).build();
    }
}
