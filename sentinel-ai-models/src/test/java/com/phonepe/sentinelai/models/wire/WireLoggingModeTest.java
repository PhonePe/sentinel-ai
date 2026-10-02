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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link WireLoggingMode}: level gates and override resolution.
 */
class WireLoggingModeTest {

    @Test
    void testFramesLogsEverything() {
        assertTrue(WireLoggingMode.FRAMES.logsResponses());
        assertTrue(WireLoggingMode.FRAMES.logsFrames());
    }

    @Test
    void testOffLogsNothing() {
        assertFalse(WireLoggingMode.OFF.logsResponses());
        assertFalse(WireLoggingMode.OFF.logsFrames());
    }

    @Test
    void testOnLogsResponsesNotFrames() {
        assertTrue(WireLoggingMode.ON.logsResponses());
        assertFalse(WireLoggingMode.ON.logsFrames());
    }

    @Test
    void testResolveBlankValueKeepsConfigured() {
        final var previous = System.getProperty(WireLoggingMode.SYSTEM_PROPERTY);
        try {
            System.setProperty(WireLoggingMode.SYSTEM_PROPERTY, " ");
            assertEquals(WireLoggingMode.ON, WireLoggingMode.resolve(WireLoggingMode.ON));
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

    @Test
    void testResolveDefaultsToOn() {
        assertEquals(WireLoggingMode.ON, WireLoggingMode.resolve(null));
    }

    @Test
    void testResolveInvalidValueKeepsConfigured() {
        final var previous = System.getProperty(WireLoggingMode.SYSTEM_PROPERTY);
        try {
            System.setProperty(WireLoggingMode.SYSTEM_PROPERTY, "garbage");
            assertEquals(WireLoggingMode.OFF, WireLoggingMode.resolve(WireLoggingMode.OFF));
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

    @Test
    void testResolveSystemPropertyOverrides() {
        final var previous = System.getProperty(WireLoggingMode.SYSTEM_PROPERTY);
        try {
            System.setProperty(WireLoggingMode.SYSTEM_PROPERTY, "frames");
            assertEquals(WireLoggingMode.FRAMES, WireLoggingMode.resolve(WireLoggingMode.OFF));
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
