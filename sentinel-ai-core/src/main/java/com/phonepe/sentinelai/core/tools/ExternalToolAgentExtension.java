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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * @author ankush.nakaskar
 */
@Slf4j
public abstract class ExternalToolAgentExtension<R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {


    @Override
    public void addAdditionalToolMetaData(R request,
                                          AgentRunContext<R> metadata,
                                          A agent) {
        try {
            log.info("Ankush addAdditionalToolMetaData:: Into External tool extensions with meta ");
            Map<String, Object> customParams = Objects.requireNonNullElse(metadata.getRequestMetadata()
                    .getCustomParams(), new HashMap<>());
            Map<String, Object> newCustomParams = new HashMap<>(customParams);
            newCustomParams.put("Ankush", "TestingKey:: AuthToken");
            metadata.getRequestMetadata().setCustomParams(newCustomParams);
            log.info("Ankush addAdditionalToolMetaData with after key population:: Into External tool extensions with meta {}",
                     metadata.getRequestMetadata().getCustomParams());
        }
        catch (Exception exception) {
            log.error("Exceptiion in addAdditionalToolMetaData ", exception);
        }

    }

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(R request,
                                                         AgentRunContext<R> metadata,
                                                         A agent,
                                                         ProcessingMode processingMode) {
        log.info("Ankush additionalSystemPrompts:: Into External tool extensions with metadata");
        return new ExtensionPromptSchema(List.of(), List.of());
    }

    @Override
    public void consume(JsonNode output, A agent) {
        log.info("Ankush consume:: Into External tool extensions with output ");
    }

    @Override
    public List<FactList> facts(R request,
                                AgentRunContext<R> context,
                                A agent) {
        log.info("Ankush facts:: Into External tool extensions with request {}", request);
        log.info("Ankush facts:: Into External tool extensions with context {}", context);
        return List.of();
    }

    public abstract Map<String, Object> getExternalToolArguments(R request,
                                                                 AgentRunContext<R> metadata,
                                                                 A agent);

    @Override
    public String name() {
        return "external-tool-agent-extension";
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        return Optional.empty();
    }
}
