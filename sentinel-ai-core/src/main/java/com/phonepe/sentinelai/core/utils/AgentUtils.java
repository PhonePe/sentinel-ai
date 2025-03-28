package com.phonepe.sentinelai.core.utils;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import lombok.experimental.UtilityClass;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Various small utilities for agent to perform tasks.
 */
@UtilityClass
public class AgentUtils {
    public static <R, D> String userId(AgentRunContext<R> context) {
        return context.getRequestMetadata() != null
               ? context.getRequestMetadata().getUserId()
               : null;
    }

    public static <R, D> String sessionId(AgentRunContext<R> context) {
        return context.getRequestMetadata() != null
               ? context.getRequestMetadata().getSessionId()
               : null;
    }

    public static Throwable rootCause(final Throwable leaf) {
        Throwable cause = leaf;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    public static <T> T safeGet(Supplier<T> supplier, T defaultValue) {
        return Objects.requireNonNullElse(supplier.get(), defaultValue);
    }

    public static int safeGetInt(Supplier<Integer> supplier, int defaultValue) {
        return safeGet(supplier, defaultValue);
    }

    public static int safeGetInt(Supplier<Integer> supplier) {
        return safeGetInt(supplier, 0);
    }
}
