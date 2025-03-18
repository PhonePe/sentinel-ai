package com.phonepe.sentinelai.core.utils;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import lombok.experimental.UtilityClass;

/**
 * Various small utilities for agent to perform tasks.
 */
@UtilityClass
public class AgentUtils {
    public static <R, D> String userId(AgentRunContext<D, R> context) {
        return context.getRequestMetadata() != null
               ? context.getRequestMetadata().getUserId()
               : null;
    }

    public static <R, D> String sessionId(AgentRunContext<D, R> context) {
        return context.getRequestMetadata() != null
               ? context.getRequestMetadata().getSessionId()
               : null;
    }
}
