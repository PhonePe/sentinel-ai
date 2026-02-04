/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.core.utils;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

import com.google.common.base.Strings;

/**
 * Loads variables from environment
 */
@UtilityClass
@Slf4j
public class EnvLoader {
    private static final Dotenv DOTENV = buildDotenv();

    /**
     * Builds the Dotenv instance based on the 'dotenv.file' system property
     * @return the Dotenv instance
     */
    public Dotenv buildDotenv() {
        final var dotFilePath = System.getProperty("dotenv.file");
        final var dotEnvConfig = Dotenv.configure()
                .ignoreIfMalformed()
                .ignoreIfMissing();
        if(!Strings.isNullOrEmpty(dotFilePath)) {
            log.info("System property 'dotenv.file' is set to: {}", dotFilePath);
            //get directory from file dotFilePath
            if(dotFilePath.contains("/")) {
                final var directory = dotFilePath.substring(0, dotFilePath.lastIndexOf("/"));
                dotEnvConfig.directory(directory);
                log.info("Loading dotenv from directory: {}", directory);
            }
            if(!dotFilePath.endsWith("/")) {
                final var filename = dotFilePath.substring(dotFilePath.lastIndexOf("/") + 1);
                dotEnvConfig.filename(filename);
                log.info("Loading dotenv from file: {}", dotFilePath);
            }
        }
        else {
            log.info("No 'dotenv.file' system property set. using default dotenv loading behavior.");
        }
        return dotEnvConfig.load();
    }

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
        return readEnv(DOTENV, variable, defaultValue);
    }

    /**
     * Reads an environment variable
     * @param dotenv the dotenv instance
     * @param variable the name of the variable
     * @param defaultValue the default value
     * @return the value of the variable
     */
    public static String readEnv(Dotenv dotenv, final String variable, final String defaultValue) {
        // Implementer's note: Do not replace with Objects.requires.. methods, we decide to support null defaultValue
        var value = dotenv.get(variable);
        if (value == null) {
            value = System.getenv(variable);
        }
        return value != null ? value : defaultValue;
    }
}
