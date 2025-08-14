package com.phonepe.sentinelai.core.utils;

import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.IdentityOutputGenerator;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Various small utilities for agent to perform tasks.
 */
@UtilityClass
public class AgentUtils {
    public static <R> String userId(AgentRunContext<R> context) {
        return context.getRequestMetadata() != null
               ? context.getRequestMetadata().getUserId()
               : null;
    }

    public static <R> String sessionId(AgentRunContext<R> context) {
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

    public static String id(String... args) {
        return String.join("_",
                           Arrays.stream(args)
                                   .map(AgentUtils::lowerCamel)
                                   .toList())
                .replaceAll("[\\s\\p{Punct}]", "_").toLowerCase();
    }

    public static String lowerCamel(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // 1. Convert spaces to underscores
        String temp = input.replace(' ', '_');

        // 2. Add underscore before uppercase letters that follow lowercase letters
        // (e.g., "camelCase" -> "camel_Case")
        temp = temp.replaceAll("([a-z])([A-Z])", "$1_$2");

        // 3. Add underscore before uppercase letters that follow numbers
        // (e.g., "version1Point2" -> "version1_Point2")
        temp = temp.replaceAll("(\\d)([A-Z])", "$1_$2");

        // 4. Convert the entire string to lowercase
        temp = temp.toLowerCase();

        //5. Squeeze multiple _ to single
        return temp.replaceAll("_+", "_");
    }

    public static AgentSetup mergeAgentSetup(final AgentSetup lhs, final AgentSetup rhs) {
        return AgentSetup.builder()
                .model(Objects.requireNonNull(value(lhs, rhs, AgentSetup::getModel), "Model is required"))
                .modelSettings(value(lhs, rhs, AgentSetup::getModelSettings))
                .mapper(Objects.requireNonNullElseGet(value(lhs, rhs, AgentSetup::getMapper), JsonUtils::createMapper))
                .executorService(Objects.requireNonNullElseGet(value(lhs, rhs, AgentSetup::getExecutorService),
                                                               Executors::newCachedThreadPool))
                .eventBus(Objects.requireNonNullElseGet(value(lhs, rhs, AgentSetup::getEventBus), EventBus::new))
                .outputGenerationMode(Objects.requireNonNullElse(value(lhs, rhs, AgentSetup::getOutputGenerationMode),
                                                                    OutputGenerationMode.TOOL_BASED))
                .outputGenerationTool(Objects.requireNonNullElseGet(value(lhs, rhs, AgentSetup::getOutputGenerationTool),
                                                                    IdentityOutputGenerator::new))
                .build();
    }

    public static <T, R> R value(final T lhs, final T rhs, Function<T, R> mapper) {
        final var obj = lhs == null ? rhs : lhs;
        if (null != obj) {
            return mapper.apply(obj);
        }
        return null;
    }
}
