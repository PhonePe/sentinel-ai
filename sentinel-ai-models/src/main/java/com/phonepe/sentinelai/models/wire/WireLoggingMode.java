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

package com.phonepe.sentinelai.models.wire;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Wire payload logging level of a {@code ConfiguredModel}. The wire payload is the exact JSON
 * sent to and received from the provider; it makes protocol debugging easier.
 */
public enum WireLoggingMode {

    /**
     * Logs the request sent to the provider, the final response received from it and the body
     * of every failed call. Raw stream frames are not logged. This is the default level; it
     * matches the request and response logging of the old SimpleOpenAI model.
     */
    ON,

    /**
     * {@link #ON} plus every raw stream frame. Use this level to debug streaming protocols;
     * a stream emits one log line per frame.
     */
    FRAMES,

    /**
     * Logs nothing; for deployments that do not want wire payloads in logs.
     */
    OFF;

    /**
     * System property that overrides the configured mode.
     */
    static final String SYSTEM_PROPERTY = "sentinel.wire.logging";

    /**
     * Environment variable that overrides the mode when the system property is unset.
     */
    static final String ENV_VARIABLE = "SENTINEL_WIRE_LOGGING";

    private static final Logger LOGGER = LoggerFactory.getLogger(WireLoggingMode.class);

    /**
     * Resolves the effective mode: the system property {@value #SYSTEM_PROPERTY} first, then
     * the environment variable {@value #ENV_VARIABLE}, then the configured mode.
     *
     * @param configured Mode chosen through the model builder; may be null.
     * @return The effective mode.
     */
    public static WireLoggingMode resolve(final WireLoggingMode configured) {
        final var effectiveConfigured = configured == null ? ON : configured;
        final var overridden = System.getProperty(SYSTEM_PROPERTY);
        if (overridden != null && !overridden.isBlank()) {
            return parseOrWarn(overridden, effectiveConfigured);
        }
        final var env = System.getenv(ENV_VARIABLE);
        if (env != null && !env.isBlank()) {
            return parseOrWarn(env, effectiveConfigured);
        }
        return effectiveConfigured;
    }

    private static WireLoggingMode parseOrWarn(final String value, final WireLoggingMode fallback) {
        try {
            return WireLoggingMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        }
        catch (final IllegalArgumentException e) {
            LOGGER.warn("Invalid wire logging mode [{}]; using [{}]", value, fallback);
            return fallback;
        }
    }

    /**
     * @return True when every raw stream frame must be logged.
     */
    public boolean logsFrames() {
        return this == FRAMES;
    }

    /**
     * @return True when request bodies and final responses must be logged.
     */
    public boolean logsResponses() {
        return this != OFF;
    }
}
