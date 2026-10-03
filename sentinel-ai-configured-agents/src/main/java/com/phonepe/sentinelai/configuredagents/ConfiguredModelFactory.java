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

import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.models.ConfiguredModel;

import lombok.AllArgsConstructor;

/**
 * A model factory that creates {@link ConfiguredModel} instances based on the provided agent
 * configuration. The model name from the model configuration replaces the default model name;
 * every other setting (base URL, auth, protocol, client, options, transformers, retry policy) is
 * inherited from the default model. Only {@link ConfiguredModel} is supported as the default
 * model.
 */
@AllArgsConstructor
public class ConfiguredModelFactory implements ModelFactory {

    @Override
    public Model build(AgentConfiguration agentConfig,
                       final Model defaultModel) {
        final var providedSetting = agentConfig.getModelConfiguration();
        if (providedSetting == null) {
            return defaultModel;
        }
        if (defaultModel instanceof ConfiguredModel configuredModel) {
            final var modelName = providedSetting.getName();
            return ConfiguredModel.builder()
                    .modelName(modelName)
                    .modelId(configuredModel.getModelId())
                    .provider(configuredModel.getProvider())
                    .protocol(configuredModel.getProtocol())
                    .httpClient(configuredModel.getHttpClient())
                    .modelOptions(configuredModel.getModelOptions())
                    .tokenCounter(configuredModel.getTokenCounter())
                    .requestTransformers(configuredModel.getRequestTransformers())
                    .requestRetryPolicy(configuredModel.getRequestRetryPolicy())
                    .wireLogging(configuredModel.getWireLogging())
                    .build();
        }
        throw new IllegalArgumentException("Unsupported model type: " + defaultModel
                .getClass()
                .getSimpleName());
    }
}
