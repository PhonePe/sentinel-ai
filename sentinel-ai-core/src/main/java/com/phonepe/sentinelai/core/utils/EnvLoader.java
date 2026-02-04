package com.phonepe.sentinelai.core.utils;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.experimental.UtilityClass;

import java.util.Objects;

/**
 * Loads variables from environment
 */
@UtilityClass
public class EnvLoader {

    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    /**
     * Reads an environment variable
     * @param variable the name of the variable
     * @return the value of the variable
     */
    public static String readEnv(final String variable) {
        return Objects.requireNonNull(readEnv(variable, null),
                                      "Please set environment variable: %s".formatted(variable));
    }

    /**
     * Reads an environment variable
     * @param variable the name of the variable
     * @param defaultValue the default value
     * @return the value of the variable
     */
    public static String readEnv(final String variable, final String defaultValue) {
        var value = DOTENV.get(variable);
        if (value == null) {
            value = System.getenv(variable);
        }
        return value != null ? value : defaultValue;
    }
}
