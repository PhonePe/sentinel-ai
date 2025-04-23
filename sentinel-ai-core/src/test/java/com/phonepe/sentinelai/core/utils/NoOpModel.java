package com.phonepe.sentinelai.core.utils;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.tools.ExecutableTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A model that does absolutely nothing.
 */
public class NoOpModel implements Model {


    @Override
    public <R, T, A extends Agent<R, T, A>> CompletableFuture<AgentOutput<T>> exchange_messages(
            AgentRunContext<R> context,
            Class<T> responseType,
            Map<String, ExecutableTool> tools,
            Agent.ToolRunner<R> toolRunner,
            List<AgentExtension> extensions,
            A agent) {
        throw new UnsupportedOperationException("NoOpModel does not support completions");
    }
}
