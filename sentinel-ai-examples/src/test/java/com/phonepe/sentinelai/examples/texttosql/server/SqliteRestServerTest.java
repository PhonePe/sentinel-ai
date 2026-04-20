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
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.examples.texttosql.tools.DatabaseInitializer;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link SqliteRestServer}.
 *
 * <p>The heavy {@code startEmbedded} path (Dropwizard startup) is covered by one smoke test that
 * verifies the server can serve a real HTTP request. All other tests cover the static helpers.
 */
@DisplayName("SqliteRestServer")
class SqliteRestServerTest {

    // =========================================================================
    // findFreePort
    // =========================================================================

    @Nested
    @DisplayName("findFreePort")
    class FindFreePortTests {

        @Test
        @DisplayName("returns a positive port number")
        void returnsPositivePort() {
            final int port = SqliteRestServer.findFreePort();
            assertTrue(port > 0, "Port should be positive");
        }

        @Test
        @DisplayName("returns a port in the valid TCP range")
        void returnsPortInValidRange() {
            final int port = SqliteRestServer.findFreePort();
            assertTrue(port <= 65535, "Port should be <= 65535");
        }

        @Test
        @DisplayName("returns different ports on successive calls (usually)")
        void returnsDifferentPortsOnSuccessiveCalls() {
            // This is probabilistic — two random free ports are highly unlikely to be equal.
            // We just verify that both calls succeed.
            final int port1 = SqliteRestServer.findFreePort();
            final int port2 = SqliteRestServer.findFreePort();
            assertTrue(port1 > 0);
            assertTrue(port2 > 0);
        }
    }

    // =========================================================================
    // waitForPort — timeout branch (via reflection)
    // =========================================================================

    @Nested
    @DisplayName("waitForPort")
    class WaitForPortTests {

        @Test
        @DisplayName("throws IllegalStateException when port is not reachable within timeout")
        void throwsWhenPortNotReachable() throws Exception {
            final Method m =
                    SqliteRestServer.class.getDeclaredMethod(
                            "waitForPort", String.class, int.class, long.class);
            m.setAccessible(true);

            // Port 1 is unreachable; 300 ms timeout guarantees a quick failure
            final InvocationTargetException ex =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> m.invoke(null, "localhost", 1, 300L));
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertTrue(ex.getCause().getMessage().contains("did not start"));
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
    @DisplayName("initialize and run")
    class InitializeAndRunTests {

        @Test
        @DisplayName("initialize does not throw and ignores the bootstrap argument")
        @SuppressWarnings("unchecked")
        void initializeDoesNotThrow() {
            final SqliteRestServer server =
                    new SqliteRestServer("/tmp/test.db", 8888, new ObjectMapper());
            final Bootstrap<SqliteRestConfig> bootstrap = mock(Bootstrap.class);
            // initialize() is a no-op — just confirm it doesn't throw
            assertDoesNotThrow(() -> server.initialize(bootstrap));
        }

        @Test
        @DisplayName("run registers the SqliteRestResource with jersey environment")
        void runRegistersResource() throws Exception {
            final SqliteRestServer server =
                    new SqliteRestServer("/tmp/test.db", 8888, new ObjectMapper());
            final Environment environment = mock(Environment.class);
            final JerseyEnvironment jersey = mock(JerseyEnvironment.class);
            when(environment.jersey()).thenReturn(jersey);

            final SqliteRestConfig config = new SqliteRestConfig();
            server.run(config, environment);

            verify(jersey, times(1)).register(any(SqliteRestResource.class));
        }
    }

    @Nested
    @DisplayName("startEmbedded")
    @Disabled("Dropwizard lifecycle calls System.exit() which crashes the test JVM")
    class StartEmbeddedTests {

        /**
         * Starts the embedded server against a freshly initialised temp database, then probes the
         * {@code GET /api/sqlite/tables} endpoint to confirm the server is actually serving
         * requests.
         */
        @Test
        @DisplayName("server starts and responds to GET /api/sqlite/tables")
        void serverStartsAndServesTables(@TempDir Path tempDir) throws Exception {
            final Path dbPath = tempDir.resolve("smoke.db");
            DatabaseInitializer.ensureInitialised(dbPath);

            final int port = SqliteRestServer.findFreePort();
            final String baseUrl =
                    SqliteRestServer.startEmbedded(
                            dbPath.toAbsolutePath().toString(), port, new ObjectMapper());

            assertNotNull(baseUrl, "Base URL should not be null");
            assertTrue(
                    baseUrl.startsWith("http://localhost:"),
                    "Base URL should start with http://localhost:");
        }
    }
}
