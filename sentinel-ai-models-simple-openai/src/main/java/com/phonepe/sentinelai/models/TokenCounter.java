package com.phonepe.sentinelai.models;

import java.util.List;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

/**
 * Interface for counting tokens in messages.
 * By default, Sentinel AI uses OpenAICompletionsTokenCounter for OpenAI models.
 * However certain proviers like anthropic have an endpoint thar can provide precise counts. Override that if needed.
 */
public interface TokenCounter {
    /**
     * Count tokens in the given text
     *
     * @param messages Messages to count tokens in
     * @return Number of tokens in the messages
     */
    int estimateTokenCount(final List<AgentMessage> messages);
    
}
