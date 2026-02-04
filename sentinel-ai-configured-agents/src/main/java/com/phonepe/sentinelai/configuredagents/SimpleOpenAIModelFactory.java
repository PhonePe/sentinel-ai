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
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import lombok.AllArgsConstructor;

/**
 * A simple model factory that creates SimpleOpenAIModel instances based on the provided agent configuration.
 * The way it works is that, the name from the modelConfig is passed to a new instance of SimpleOpenAIModel, this name
 * is used by SimpleOpenAI model to fetch the relevant API provider from the {@link com.phonepe.sentinelai.models.ChatCompletionServiceFactory}.
 */
@AllArgsConstructor
public class SimpleOpenAIModelFactory implements ModelFactory {

    @Override
    public Model build(AgentConfiguration agentConfig, final Model defaultModel) {
        final var providedSetting = agentConfig.getModelConfiguration();
        if (providedSetting == null) {
            return defaultModel;
        }
        if(defaultModel instanceof SimpleOpenAIModel<?> simpleOpenAIModel) {
            final var aiProviderFactory = simpleOpenAIModel.getOpenAIProviderFactory();
            final var modelName = providedSetting.getName();
            final var serviceProvider = aiProviderFactory.get(modelName);
            return new SimpleOpenAIModel<>(modelName,
                                           serviceProvider,
                                           simpleOpenAIModel.getMapper(),
                                           simpleOpenAIModel.getModelOptions());
        }
        throw new IllegalArgumentException("Unsupported model type: " + defaultModel.getClass().getSimpleName());
    }
}
