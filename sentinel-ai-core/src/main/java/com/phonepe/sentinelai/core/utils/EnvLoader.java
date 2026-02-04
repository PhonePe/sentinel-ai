package com.phonepe.sentinelai.core.utils;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.experimental.UtilityClass;

import java.util.Objects;
import java.util.Optional;

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
    public static Optional<String> readEnv(final String variable) {
        return Optional.ofNullable(readEnv(variable, null));
    }

    /**
     * Reads an environment variable
     * @param variable the name of the variable
     * @param defaultValue the default value
     * @return the value of the variable
     */
    public static String readEnv(final String variable, final String defaultValue) {
        // Implementer's note: Do not replace with Objects.requires.. methods, we decide to support null defaultValue
        var value = DOTENV.get(variable);
        if (value == null) {
            value = System.getenv(variable);
        }
        return value != null ? value : defaultValue;
    }
}
