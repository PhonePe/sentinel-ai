package com.phonepe.sentinelai.configuredagents;

import com.phonepe.sentinelai.core.agent.AgentSetup;

/**
 * Provides a fresh AgentSetup based on the configuration provided and the model factory. Uses the source config
 * to fill in any blanks not provided in the agent configuration.
 */
@FunctionalInterface
public interface AgentSetupProvider {
    AgentSetup from(final AgentSetup source,
                    final AgentConfiguration agentConfiguration, ModelFactory modelFactory);
}
