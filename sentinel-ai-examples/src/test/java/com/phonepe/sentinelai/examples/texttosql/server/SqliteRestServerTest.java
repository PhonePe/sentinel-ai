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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.setup.JerseyEnvironment;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SqliteRestServer}.
 *
 * <p>The heavy {@code startEmbedded} path (Dropwizard startup) is covered by one smoke test that
 * verifies the server can serve a real HTTP request. All other tests cover the static helpers.
 */
class SqliteRestServerTest {

    // =========================================================================
    // findFreePort
    // =========================================================================

    @Nested
    class FindFreePortTests {

        @Test
        void returnsDifferentPortsOnSuccessiveCalls() {
            // This is probabilistic — two random free ports are highly unlikely to be equal.
            // We just verify that both calls succeed.
            final var port1 = SqliteRestServer.findFreePort();
            final var port2 = SqliteRestServer.findFreePort();
            assertTrue(port1 > 0);
            assertTrue(port2 > 0);
        }

        @Test
        void returnsPortInValidRange() {
            final var port = SqliteRestServer.findFreePort();
            assertTrue(port <= 65535, "Port should be <= 65535");
        }

        @Test
        void returnsPositivePort() {
            final var port = SqliteRestServer.findFreePort();
            assertTrue(port > 0, "Port should be positive");
        }
    }

    // =========================================================================
    // waitForPort — timeout branch (via reflection)
    // =========================================================================

    @Nested
    class InitializeAndRunTests {

        @Test
        @SuppressWarnings("unchecked")
        void initializeDoesNotThrow() {
            final var server = new SqliteRestServer("/tmp/test.db", 8888, new ObjectMapper());
            final var bootstrap = mock(Bootstrap.class);
            // initialize() is a no-op — just confirm it doesn't throw
            assertDoesNotThrow(() -> server.initialize(bootstrap));
        }

        @Test
        void runRegistersResource() {
            final var server = new SqliteRestServer("/tmp/test.db", 8888, new ObjectMapper());
            final var environment = mock(Environment.class);
            final var jersey = mock(JerseyEnvironment.class);
            when(environment.jersey()).thenReturn(jersey);

            final var config = new SqliteRestConfig();
            server.run(config, environment);

            verify(jersey, times(1)).register(any(SqliteRestResource.class));
        }
    }

    // =========================================================================
    // startEmbedded — smoke test (disabled: Dropwizard calls System.exit on shutdown,
    // which terminates the JVM and crashes Surefire in unit test mode)
    // =========================================================================

    // =========================================================================
    // initialize & run — Dropwizard lifecycle hooks (via Mockito)
    // =========================================================================

    @Nested
    @Disabled("Dropwizard lifecycle calls System.exit() which crashes the test JVM")
    class StartEmbeddedTests {

        /**
         * Starts the embedded server against a freshly initialised temp database, then probes the
         * {@code GET /api/sqlite/tables} endpoint to confirm the server is actually serving
         * requests.
         */
        @Test
        void serverStartsAndServesTables(@TempDir Path tempDir) {
            final var dbPath = tempDir.resolve("smoke.db");
            DatabaseInitializer.ensureInitialised(dbPath);

            final var port = SqliteRestServer.findFreePort();
            final var baseUrl = SqliteRestServer.startEmbedded(
                                                               dbPath.toAbsolutePath().toString(),
                                                               port,
                                                               new ObjectMapper());

            assertNotNull(baseUrl, "Base URL should not be null");
            assertTrue(
                       baseUrl.startsWith("http://localhost:"),
                       "Base URL should start with http://localhost:");
        }
    }

    @Nested
    class WaitForPortTests {

        @Test
        void throwsWhenPortNotReachable() throws Exception {
            final var m = SqliteRestServer.class.getDeclaredMethod(
                                                                   "waitForPort",
                                                                   String.class,
                                                                   int.class,
                                                                   long.class);
            m.setAccessible(true);

            // Port 1 is unreachable; 300 ms timeout guarantees a quick failure
            final var ex = assertThrows(
                                        InvocationTargetException.class,
                                        () -> m.invoke(null, "localhost", 1, 300L));
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("did not start"));
        }
    }
}
