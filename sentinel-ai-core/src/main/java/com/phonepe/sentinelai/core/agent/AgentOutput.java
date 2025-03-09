package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.errors.SentinelError;
import lombok.Value;

import java.util.List;

/**
 *
 */
@Value
public class AgentOutput<T> {
    T data;
    List<AgentMessage> newMessages;
    List<AgentMessage> allMessages;
    ModelUsageStats usage;
    SentinelError error;

    public static <T> AgentOutput<T> success(T data, List<AgentMessage> newMessages, List<AgentMessage> allMessages, ModelUsageStats usage) {
        return new AgentOutput<>(data, newMessages, allMessages, usage, SentinelError.success());
    }

    public static <T> AgentOutput<T> error(List<AgentMessage> oldMessages, ModelUsageStats stats, SentinelError error) {
        return new AgentOutput<>(null, List.of(), oldMessages, stats, error);
    }
}
