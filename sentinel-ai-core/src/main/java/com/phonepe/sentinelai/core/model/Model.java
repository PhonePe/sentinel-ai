package com.phonepe.sentinelai.core.model;

import com.phonepe.sentinelai.core.agent.DirectRunOutput;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import org.apache.commons.lang3.NotImplementedException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Abstract representation for a LLM model
 */
public interface Model {

    default <R, T, A extends Agent<R, T, A>> CompletableFuture<DirectRunOutput> runDirect(
            AgentRunContext<R> context,
            String prompt,
            AgentExtension.AgentExtensionOutputDefinition outputDefinition,
            List<AgentMessage> messages) {
        throw new UnsupportedOperationException("Direct run is not supported by this model");
    }

    <R, T, A extends Agent<R, T, A>> CompletableFuture<AgentOutput<T>> exchange_messages(
            AgentRunContext<R> context,
            Class<T> responseType,
            Map<String, ExecutableTool> tools,
            Agent.ToolRunner<R> toolRunner,
            List<AgentExtension> extensions,
            A agent);

    default <R, T, A extends Agent<R, T, A>> CompletableFuture<AgentOutput<byte[]>> exchange_messages_streaming(
            AgentRunContext<R> context,
            Map<String, ExecutableTool> tools,
            Agent.ToolRunner<R> toolRunner,
            List<AgentExtension> extensions,
            A agent,
            Consumer<byte[]> streamHandler) {
        throw new NotImplementedException();
    }
}
