package com.phonepe.sentinelai.configuredagents;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import lombok.AllArgsConstructor;

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

        final var modelSettings = source.getModelSettings();
        return AgentSetup.builder()
                .mapper(source.getMapper())
                .model(modelFactory.build(agentConfiguration, source.getModel()))
                .modelSettings(modelSettings)
                .executorService(source.getExecutorService())
                .eventBus(source.getEventBus())
                .outputGenerationMode(source.getOutputGenerationMode())
                .outputGenerationTool(source.getOutputGenerationTool())
                .retrySetup(source.getRetrySetup())
                .build();
    }
}
