package com.phonepe.sentinelai.configuredagents;

import com.phonepe.sentinelai.core.model.Model;

/**
 * A generic interface to dynamically create models on the fly.
 * The model from the parent agent setup will be passed as the defaultModel, implementation can decide to return it or
 * throw an exception based on the circumstances.
 */
@FunctionalInterface
public interface ModelFactory {
    Model build(AgentConfiguration agentConfig, final Model defaultModel);
}
