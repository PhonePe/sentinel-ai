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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link WireLoggingMode}: level gates and override resolution.
 */
class WireLoggingModeTest {

    static Stream<Arguments> resolveScenarios() {
        return Stream.of(Arguments.of("blank value keeps configured", " ", WireLoggingMode.ON, WireLoggingMode.ON),
                         Arguments.of("invalid value keeps configured",
                                      "garbage",
                                      WireLoggingMode.OFF,
                                      WireLoggingMode.OFF),
                         Arguments.of("valid value overrides configured",
                                      "frames",
                                      WireLoggingMode.OFF,
                                      WireLoggingMode.FRAMES));
    }

    @ParameterizedTest
    @CsvSource({
            "FRAMES, true, true",
            "ON, true, false",
            "OFF, false, false"
    })
    void testLevelGates(final WireLoggingMode mode, final boolean logsResponses, final boolean logsFrames) {
        assertEquals(logsResponses, mode.logsResponses(), mode + " responses gate");
        assertEquals(logsFrames, mode.logsFrames(), mode + " frames gate");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolveScenarios")
    void testResolve(final String name,
                     final String systemPropertyValue,
                     final WireLoggingMode configured,
                     final WireLoggingMode expected) {
        withSystemProperty(systemPropertyValue,
                           () -> assertEquals(expected, WireLoggingMode.resolve(configured), name));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolveScenarios")
    void testResolveNullConfiguredDefaultsToOn(final String name,
                                               final String systemPropertyValue,
                                               final WireLoggingMode configured,
                                               final WireLoggingMode expected) {
        // Null configured mode with no override resolves to ON.
        withSystemProperty(null, () -> assertEquals(WireLoggingMode.ON, WireLoggingMode.resolve(null), name));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("resolveScenarios")
    void testResolveWithNoSystemPropertyFallsBackToConfigured(final String name,
                                                              final String systemPropertyValue,
                                                              final WireLoggingMode configured,
                                                              final WireLoggingMode expected) {
        // Without the system property set, the configured mode always wins.
        withSystemProperty(null, () -> assertEquals(configured, WireLoggingMode.resolve(configured), name));
    }

    private void withSystemProperty(final String value, final Runnable assertion) {
        final var previous = System.getProperty(WireLoggingMode.SYSTEM_PROPERTY);
        try {
            if (value == null) {
                System.clearProperty(WireLoggingMode.SYSTEM_PROPERTY);
            }
            else {
                System.setProperty(WireLoggingMode.SYSTEM_PROPERTY, value);
            }
            assertion.run();
        }
        finally {
            if (previous == null) {
                System.clearProperty(WireLoggingMode.SYSTEM_PROPERTY);
            }
            else {
                System.setProperty(WireLoggingMode.SYSTEM_PROPERTY, previous);
            }
        }
    }
}
