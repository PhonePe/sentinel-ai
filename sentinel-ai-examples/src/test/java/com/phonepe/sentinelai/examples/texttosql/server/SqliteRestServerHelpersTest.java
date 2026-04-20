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

package com.phonepe.sentinelai.examples.texttosql.server;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the package-visible helper methods of {@link SqliteRestServer}.
 *
 * <p>These helpers are directly accessible from the same package — no reflection needed.
 */
@DisplayName("SqliteRestServer helpers")
class SqliteRestServerHelpersTest {

    // =========================================================================
    // buildInlineConfig
    // =========================================================================

    @Nested
    @DisplayName("buildInlineConfig")
    class BuildInlineConfigTests {

        @Test
        @DisplayName("returns path to a temp file that exists on disk")
        void returnsTempFileThatExists() throws Exception {
            final String configPath = SqliteRestServer.buildInlineConfig(9876);
            assertNotNull(configPath);
            assertTrue(Files.exists(Path.of(configPath)), "Temp config file should exist");
        }

        @Test
        @DisplayName("substitutes port into the generated config file content")
        void substitutesPortInContent() throws Exception {
            final int port = 12345;
            final String configPath = SqliteRestServer.buildInlineConfig(port);
            final String content = Files.readString(Path.of(configPath));
            assertTrue(
                    content.contains(String.valueOf(port)),
                    "Generated config should contain the requested port number");
        }

        @Test
        @DisplayName("does not contain shell-style placeholder tokens after substitution")
        void noUnresolvedPlaceholders() throws Exception {
            final String configPath = SqliteRestServer.buildInlineConfig(8080);
            final String content = Files.readString(Path.of(configPath));
            assertFalse(
                    content.contains("${DW_PORT"),
                    "Generated config must not contain unresolved DW_PORT placeholder");
            assertFalse(
                    content.contains("${DW_ADMIN_PORT"),
                    "Generated config must not contain unresolved DW_ADMIN_PORT placeholder");
        }

        @Test
        @DisplayName("admin port is port + 1")
        void adminPortIsPortPlusOne() throws Exception {
            final int port = 7777;
            final String configPath = SqliteRestServer.buildInlineConfig(port);
            final String content = Files.readString(Path.of(configPath));
            assertTrue(
                    content.contains(String.valueOf(port + 1)),
                    "Generated config should contain the admin port (port + 1)");
        }
    }

    // =========================================================================
    // waitForPort
    // =========================================================================

    @Nested
    @DisplayName("waitForPort")
    class WaitForPortTests {

        @Test
        @DisplayName("throws IllegalStateException when port is not reachable within timeout")
        void throwsWhenPortNotReachable() {
            assertThrows(
                    IllegalStateException.class,
                    () -> SqliteRestServer.waitForPort("localhost", 1, 300L));
        }

        @Test
        @DisplayName("error message mentions the timeout duration")
        void errorMessageMentionsTimeout() {
            final IllegalStateException ex =
                    assertThrows(
                            IllegalStateException.class,
                            () -> SqliteRestServer.waitForPort("localhost", 1, 300L));
            assertTrue(
                    ex.getMessage().contains("did not start"),
                    "Error should say server did not start");
        }
    }
}
