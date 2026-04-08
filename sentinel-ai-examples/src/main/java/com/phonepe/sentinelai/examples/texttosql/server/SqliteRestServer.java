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

import static java.nio.file.Files.createTempFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Embedded Dropwizard application that exposes a REST API mirroring the functionality of the {@code
 * mcp-sqlite} MCP server.
 *
 * <p>The CLI starts this server in a background thread on a dynamically chosen port and injects the
 * base URL into the {@code HttpToolBox}. The server is shut down gracefully when the CLI exits.
 *
 * <h2>Available endpoints</h2>
 *
 * <pre>
 * POST /api/sqlite/query — Execute arbitrary SQL
 * GET /api/sqlite/tables — List all tables
 * GET /api/sqlite/schema/{tableName} — Get schema for a table
 * GET /api/sqlite/info — Database metadata
 * GET /api/sqlite/records/{table} — Read records (with optional filters)
 * POST /api/sqlite/records/{table} — Insert a record
 * PUT /api/sqlite/records/{table} — Update records
 * DELETE /api/sqlite/records/{table} — Delete records
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class SqliteRestServer extends Application<SqliteRestConfig> {

    private final String dbPath;
    private final int port;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Find a free TCP port on localhost. */
    @SneakyThrows
    public static int findFreePort() {
        try (var socket = new java.net.ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    /**
     * Starts the Dropwizard server in a daemon thread and returns once the server is ready to
     * accept connections.
     *
     * @param dbPath path to the SQLite database file
     * @param port HTTP port to bind (use 0 for OS-assigned free port; however Dropwizard requires a
     *     concrete port here, so the CLI picks a free port before calling this)
     * @param objectMapper shared Jackson mapper
     * @return the base URL, e.g. {@code http://localhost:8765}
     */
    @SneakyThrows
    public static String startEmbedded(String dbPath, int port, ObjectMapper objectMapper) {
        log.info("Starting embedded SQLite REST server on port {} for db: {}", port, dbPath);

        final var server = new SqliteRestServer(dbPath, port, objectMapper);

        // Dropwizard parses CLI args; we provide "server" command with an
        // inline YAML config that overrides the default ports.
        final String[] args = {"server", buildInlineConfig(port)};

        final var startFuture =
                CompletableFuture.runAsync(
                        () -> {
                            try {
                                server.run(args);
                            } catch (Exception e) {
                                throw new IllegalStateException(
                                        "Failed to start embedded SQLite REST server", e);
                            }
                        });

        // Give the server up to 30 seconds to start
        try {
            startFuture.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for server start: {}", e.getMessage());
        } catch (TimeoutException e) {
            // Timeout is expected — Dropwizard's run() blocks serving requests,
            // so we just wait briefly for the port to become available instead.
        } catch (Exception e) {
            if (!(e.getCause() instanceof java.net.BindException)) {
                log.warn("Server start completed with: {}", e.getMessage());
            }
        }

        // Wait until the port responds
        waitForPort("localhost", port, 30_000);

        final String baseUrl = "http://localhost:" + port;
        log.info("Embedded SQLite REST server is ready at {}", baseUrl);
        return baseUrl;
    }

    // -------------------------------------------------------------------------
    // Static factory — start the server embedded in the current JVM
    // -------------------------------------------------------------------------

    /**
     * Copies the bundled {@code dw/config.yml} classpath resource to a temp file, substituting the
     * concrete port numbers for the {@code ${DW_PORT:-…}} and {@code ${DW_ADMIN_PORT:-…}}
     * placeholders, and returns the absolute path of that temp file so Dropwizard can parse it.
     */
    @SneakyThrows
    private static String buildInlineConfig(int port) {
        final String template;
        try (var stream =
                SqliteRestServer.class.getClassLoader().getResourceAsStream("dw/config.yml")) {
            if (stream == null) {
                throw new IllegalStateException("Classpath resource dw/config.yml not found");
            }
            template = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        // Replace the shell-style default-value placeholders with the actual ports.
        final String yaml =
                template.replaceAll("\\$\\{DW_PORT(?::-[^}]*)?}", String.valueOf(port))
                        .replaceAll("\\$\\{DW_ADMIN_PORT(?::-[^}]*)?}", String.valueOf(port + 1));

        final var tmpFile = createTempFile("sqlite-rest-server-", ".yaml");
        java.nio.file.Files.writeString(tmpFile, yaml);
        tmpFile.toFile().deleteOnExit();
        return tmpFile.toAbsolutePath().toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Polls {@code host:port} until a TCP connection succeeds or the timeout elapses. */
    @SneakyThrows
    private static void waitForPort(String host, int port, long timeoutMs)
            throws InterruptedException {
        final long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (var socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), 500);
                return; // connected — server is ready
            } catch (Exception ignored) {
                // Not yet available; retry after a short delay
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException(
                "SQLite REST server did not start within " + timeoutMs + "ms");
    }

    @Override
    public void initialize(Bootstrap<SqliteRestConfig> bootstrap) {
        // Use the caller-supplied ObjectMapper so serialisation is consistent.
    }

    @Override
    public void run(SqliteRestConfig config, Environment environment) {
        final var resource = new SqliteRestResource(dbPath, objectMapper);
        environment.jersey().register(resource);
        log.info("SQLite REST server registered with db: {}", dbPath);
    }
}
