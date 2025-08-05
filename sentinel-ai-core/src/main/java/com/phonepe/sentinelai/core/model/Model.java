package com.phonepe.sentinelai.core.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.agent.*;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import org.apache.commons.lang3.NotImplementedException;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * Abstract representation for a LLM model
 */
public interface Model {

    default <R, T, A extends Agent<R, T, A>> CompletableFuture<DirectRunOutput> runDirect(
            AgentRunContext<R> context,
            ModelOutputDefinition outputDefinition,
            List<AgentMessage> messages) {
        final var agentSetup = context.getAgentSetup();
        return runDirect(agentSetup.getModelSettings(),
                         agentSetup.getExecutorService(),
                         outputDefinition,
                         messages);
    }

    default <R, T, A extends Agent<R, T, A>> CompletableFuture<DirectRunOutput> runDirect(
            ModelSettings modelSettings,
            ExecutorService executorService,
            ModelOutputDefinition outputDefinition,
            List<AgentMessage> messages) {
        throw new UnsupportedOperationException("Direct run is not supported by this model");
    }

    default <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessages(
            AgentRunContext<R> context,
            JsonNode responseSchema,
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner,
            List<AgentExtension<R,T,A>> extensions,
            A agent) {
        throw new NotImplementedException();
    }

    default <R, T, A extends Agent<R, T, A>> CompletableFuture<ModelOutput> exchangeMessagesStreaming(
            AgentRunContext<R> context,
            Map<String, ExecutableTool> tools,
            ToolRunner toolRunner,
            List<AgentExtension<R,T,A>> extensions,
            A agent,
            Consumer<byte[]> streamHandler) {
        throw new NotImplementedException();
    }

    CompletableFuture<ModelOutput> process(
            ModelRunContext context,
            Collection<ModelOutputDefinition> outputDefinitions,
            List<AgentMessage> oldMessages, Map<String, ExecutableTool> tools,
            ToolRunner toolRunner);
}
