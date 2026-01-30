package com.phonepe.sentinelai.session;

import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentRunContext;

public interface CompactionStrategy {
    boolean isCompactionNeeded(final AgentRunContext<?> context, final AgentOutput<?> output);
}
