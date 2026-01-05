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
    public AgentSetup from(
            AgentSetup source,
            AgentConfiguration agentConfiguration,
            ModelFactory modelFactory) {
        final var providedConfig = agentConfiguration.getModelConfiguration();
        if (providedConfig == null) {
            return source;
        }

        // Use the model settings from the provided config, else fall back to source
        final var modelSettings = Objects.requireNonNullElseGet(
                providedConfig.getSettings(), source::getModelSettings);
        final var outputGenerationMode = null != providedConfig.getOutputGenerationMode()
                                         ? providedConfig.getOutputGenerationMode()
                                         : source.getOutputGenerationMode();
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
