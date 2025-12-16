package com.phonepe.sentinelai.configuredagents;

import com.phonepe.sentinelai.core.model.Model;

/**
 *
 */
@FunctionalInterface
public interface ModelFactory {
    Model build(AgentConfiguration agentConfig, final Model defaultModel);
}
