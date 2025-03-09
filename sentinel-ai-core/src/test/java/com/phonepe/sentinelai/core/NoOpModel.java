package com.phonepe.sentinelai.core;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.CallableTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * A model that does absolutely nothing.
 */
public class NoOpModel implements Model {
    @Override
    public <R, T, D> CompletableFuture<AgentOutput<T>> exchange_messages(
            ModelSettings modelSettings,
            AgentRunContext<D, R> context,
            Class<T> responseType,
            Map<String, CallableTool> tools,
            List<AgentMessage> oldMessages,
            ExecutorService executorService,
            Agent.ToolRunner toolRunner) {
        throw new UnsupportedOperationException("NoOpModel does not support completions");
    }
}
