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

import com.phonepe.sentinelai.core.agent.AgentSetup;

import lombok.AllArgsConstructor;

import java.util.Objects;

/**
 * Provides a new setup basd on the provided configuration
 */
@AllArgsConstructor
public class ConfigDrivenAgentSetupProvider implements AgentSetupProvider {

    @Override
    public AgentSetup from(AgentSetup source, AgentConfiguration agentConfiguration, ModelFactory modelFactory) {
        final var providedConfig = agentConfiguration.getModelConfiguration();
        if (providedConfig == null) {
            return source;
        }

        // Use the model settings from the provided config, else fall back to source
        final var modelSettings = Objects.requireNonNullElseGet(providedConfig.getSettings(), source::getModelSettings);
        final var outputGenerationMode = null != providedConfig.getOutputGenerationMode() ? providedConfig
                .getOutputGenerationMode() : source.getOutputGenerationMode();
        return AgentSetup.builder()
                .mapper(source.getMapper())
                .model(modelFactory.build(agentConfiguration, source.getModel()))
                .modelSettings(modelSettings)
                .executorService(source.getExecutorService())
                .eventBus(source.getEventBus())
                .outputGenerationMode(outputGenerationMode)
                .outputGenerationTool(source.getOutputGenerationTool())
                .retrySetup(source.getRetrySetup())
                .build();
    }
}
