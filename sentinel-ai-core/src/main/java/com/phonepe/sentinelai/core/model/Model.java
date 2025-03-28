package com.phonepe.sentinelai.core.model;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.tools.CallableTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract representation for a LLM model
 */
public interface Model {
//    <R, T, D> CompletableFuture<T> completions(
//            AgentRunContext<D,R> context,
//            R request,
//            Class<T> responseType,
//            String systemPrompt,
//            Map<String, CallableTool> tools);

    <R, T, A extends Agent<R, T, A>> CompletableFuture<AgentOutput<T>> exchange_messages(
            AgentRunContext<R> context,
            Class<T> responseType,
            Map<String, CallableTool> tools,
            Agent.ToolRunner<R> toolRunner,
            List<AgentExtension> extensions,
            A agent);
}
