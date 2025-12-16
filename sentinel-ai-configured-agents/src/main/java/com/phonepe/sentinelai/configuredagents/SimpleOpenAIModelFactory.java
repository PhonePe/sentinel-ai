package com.phonepe.sentinelai.configuredagents;

import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.models.SimpleOpenAIModel;
import lombok.AllArgsConstructor;

/**
 *
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
            return new SimpleOpenAIModel<>(providedSetting.getName(),
                                           simpleOpenAIModel.getOpenAIProviderFactory(),
                                           simpleOpenAIModel.getMapper(),
                                           simpleOpenAIModel.getModelOptions());
        }
        throw new IllegalArgumentException("Unsupported model type: " + defaultModel.getClass().getSimpleName());
    }
}
