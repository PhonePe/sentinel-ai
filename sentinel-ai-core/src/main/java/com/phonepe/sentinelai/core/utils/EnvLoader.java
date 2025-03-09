package com.phonepe.sentinelai.core.utils;

import lombok.experimental.UtilityClass;

import java.util.Objects;

/**
 * Loads variables from environment
 */
@UtilityClass
public class EnvLoader {
    /**
     * Reads an environment variable
     * @param variable the name of the variable
     * @return the value of the variable
     */
    public static String readEnv(final String variable) {
        return Objects.requireNonNull(System.getenv(variable), "Please set environment variable: %s".formatted(variable));
    }
}
