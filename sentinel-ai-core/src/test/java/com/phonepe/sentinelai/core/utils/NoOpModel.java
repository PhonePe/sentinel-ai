package com.phonepe.sentinelai.core.utils;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.tools.CallableTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A model that does absolutely nothing.
 */
public class NoOpModel implements Model {


    @Override
    public <R, D, T, A extends Agent<R, D, T, A>> CompletableFuture<AgentOutput<T>> exchange_messages(
            AgentRunContext<D, R> context,
            Class<T> responseType,
            Map<String, CallableTool> tools,
            Agent.ToolRunner toolRunner,
            List<AgentExtension> extensions,
            A agent) {
        throw new UnsupportedOperationException("NoOpModel does not support completions");
    }
}
