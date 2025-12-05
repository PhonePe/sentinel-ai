package com.phonepe.sentinelai.configuredagents;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import lombok.NonNull;

import java.util.List;
import java.util.Map;

/**
 * For testing agent registration
 */
public class MathAgent extends RegisterableAgent<MathAgent> {

    public MathAgent(
            AgentConfiguration agentConfiguration,
            @NonNull AgentSetup setup) {
        super(agentConfiguration, setup, List.of(), Map.of());
    }
}
