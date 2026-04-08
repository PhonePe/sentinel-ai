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

package com.phonepe.sentinelai.examples.texttosql.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

/**
 * Utility class that initialises the SQLite e-commerce database on first run.
 *
 * <p>If the database file does not exist (or is empty), this class:
 *
 * <ol>
 *   <li>Creates the database file at the specified path.
 *   <li>Executes the bundled {@code schema.sql} DDL to create all tables and indexes.
 *   <li>Loads CSV data from the bundled {@code db/ecommercdata/*.csv} files into each table, using a plain
 *       {@link BufferedReader} (no extra dependencies).
 * </ol>
 *
 * <p>If the database already exists and has tables, it is left untouched.
 */
@Slf4j
@UtilityClass
public class DatabaseInitializer {

    private static final String[] TABLE_ORDER = {
        "users", "sellers", "catalog", "inventory", "orders"
    };

    /**
     * Ensures the SQLite database at {@code dbPath} is initialised with the bundled schema and
     * sample data.
     *
     * @param dbPath path to the SQLite file (created if it does not exist)
     */
    @SneakyThrows
    public static void ensureInitialised(Path dbPath) {
        final boolean fileExists = Files.exists(dbPath) && Files.size(dbPath) > 0;
        if (fileExists && isDatabasePopulated(dbPath)) {
            log.info("Database already exists at {} — skipping initialisation", dbPath);
            return;
        }

        log.info("Initialising e-commerce database at {}", dbPath);

        // Ensure parent directories exist
        if (dbPath.getParent() != null) {
            Files.createDirectories(dbPath.getParent());
        }

        try (Connection conn = connect(dbPath)) {
            conn.setAutoCommit(false);
            createSchema(conn);
            for (final String table : TABLE_ORDER) {
                loadCsvData(conn, table);
            }
            conn.commit();
            log.info("Database initialisation complete: {}", dbPath);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Parses a single CSV line into tokens, respecting double-quoted fields (which may contain
     * commas). Escaped inner quotes ({@code ""}) are unescaped.
     *
     * <p>This is a minimal RFC-4180-compatible parser sufficient for the bundled seed CSV files —
     * no external dependency required.
     */
    static List<String> parseCsvLine(String line) {
        final List<String> tokens = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        int i = 0;
        while (i < line.length()) {
            final char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Doubled quote inside quoted field — emit a literal quote, skip both chars
                    current.append('"');
                    i += 2;
                } else if (c == '"') {
                    inQuotes = false; // closing quote
                    i++;
                } else {
                    current.append(c);
                    i++;
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                    i++;
                } else if (c == ',') {
                    tokens.add(current.toString());
                    current.setLength(0);
                    i++;
                } else {
                    current.append(c);
                    i++;
                }
            }
        }
        tokens.add(current.toString()); // last field
        return tokens;
    }

    /**
     * Opens a resource stream by path, trying the thread context classloader first (so that
     * test-classpath resources in {@code target/test-classes} are found even when this class is
     * loaded from a cached JAR), then falling back to the class's own classloader.
     */
    private static java.io.InputStream openResource(String path) {
        final var tccl = Thread.currentThread().getContextClassLoader();
        // Strip the leading '/' that getResourceAsStream expects but ClassLoader does not
        final String stripped = path.startsWith("/") ? path.substring(1) : path;
        if (tccl != null) {
            final var stream = tccl.getResourceAsStream(stripped);
            if (stream != null) {
                return stream;
            }
        }
        return DatabaseInitializer.class.getResourceAsStream(path);
    }

    @SneakyThrows
    private static Connection connect(Path dbPath) {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    @SneakyThrows
    private static void createSchema(Connection conn) {
        final var schemaStream = openResource("/db/schema.sql");
        Objects.requireNonNull(schemaStream, "Bundled schema.sql not found on classpath");

        final String schemaSql = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);

        // Split on semicolons to execute each statement individually
        try (Statement stmt = conn.createStatement()) {
            for (final String sql : schemaSql.split(";")) {
                final String trimmed = sql.trim();
                final StringBuilder sb = new StringBuilder();
                Stream.of(trimmed.split("\n"))
                        .filter(line -> !line.trim().startsWith("--")) // skip comment lines
                        .forEach(line -> sb.append(line).append("\n"));
                val query = sb.toString().trim();
                if (!query.isEmpty()) {
                    log.info("Executing stmt: {}", query);
                    stmt.execute(query);
                }
            }
        }
        log.debug("Schema created successfully");
    }

    @SneakyThrows
    private static boolean isDatabasePopulated(Path dbPath) {
        try (Connection conn = connect(dbPath);
                var stmt = conn.createStatement();
                var rs =
                        stmt.executeQuery(
                                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    @SneakyThrows
    private static void loadCsvData(Connection conn, String table) {
        final String resource = "/db/ecommerce-data/" + table + ".csv";
        final var stream = openResource(resource);
        if (stream == null) {
            throw new IllegalStateException(
                    "No CSV file found for table '" + table + "' at " + resource);
        }

        try (var reader =
                new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            // First line = column headers
            final String headerLine = reader.readLine();
            if (headerLine == null) {
                log.warn("Empty CSV file for table '{}'", table);
                return;
            }

            final List<String> headers = parseCsvLine(headerLine);
            final int colCount = headers.size();
            final String placeholders = "?,".repeat(colCount).replaceAll(",$", "");
            final String insertSql =
                    "INSERT OR IGNORE INTO \""
                            + table
                            + "\" (\""
                            + String.join("\", \"", headers)
                            + "\") VALUES ("
                            + placeholders
                            + ")";

            try (var pstmt = conn.prepareStatement(insertSql)) {
                String csvLine;
                int count = 0;
                while ((csvLine = reader.readLine()) != null) {
                    final String trimmedLine = csvLine.trim();
                    if (trimmedLine.isEmpty()) {
                        continue;
                    }
                    final List<String> row = parseCsvLine(trimmedLine);
                    for (int i = 0; i < colCount; i++) {
                        final String val = i < row.size() ? row.get(i).trim() : "";
                        if (val.isEmpty()) {
                            pstmt.setNull(i + 1, java.sql.Types.NULL);
                        } else {
                            pstmt.setString(i + 1, val);
                        }
                    }
                    pstmt.addBatch();
                    count++;
                }
                pstmt.executeBatch();
                log.debug("Loaded {} rows into table '{}'", count, table);
            }
        }
    }

    public static void main(String[] args) {
        final Path dbPath = Path.of("sentinel-ai-examples/.data/ecommerce.db");
        ensureInitialised(dbPath);
    }
}
