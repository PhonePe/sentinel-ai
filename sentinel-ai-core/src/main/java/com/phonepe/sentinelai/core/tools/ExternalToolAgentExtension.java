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

package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.JsonNode;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.FactList;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * @author ankush.nakaskar
 */
@Slf4j
public class ExternalToolAgentExtension<R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {

    @Override
    public void addAdditionalToolMetaData(R request,
                                          AgentRunContext<R> metadata,
                                          A agent,
                                          ProcessingMode processingMode) {
        log.info("Ankush addAdditionalToolMetaData:: Into External tool extensions with meta {} request {}",
                 metadata,
                 request);
    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(R request,
                                                         AgentRunContext<R> metadata,
                                                         A agent,
                                                         ProcessingMode processingMode) {
        log.info("Ankush additionalSystemPrompts:: Into External tool extensions with metadata {} request {}",
                 metadata,
                 agent);
        return null;
    }

    @Override
    public void consume(JsonNode output, A agent) {
        log.info("Ankush consume:: Into External tool extensions with output {} agent {}", output, agent);
    }

    @Override
    public List<FactList> facts(R request,
                                AgentRunContext<R> context,
                                A agent) {
        log.info("Ankush facts:: Into External tool extensions with context {} request {}", context, request);
        return List.of();
    }

    @Override
    public String name() {
        return "external-tool-agent-extension";
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        return Optional.empty();
    }
}
