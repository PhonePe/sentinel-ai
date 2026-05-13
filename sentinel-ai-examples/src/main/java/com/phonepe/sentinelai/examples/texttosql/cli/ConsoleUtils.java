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

package com.phonepe.sentinelai.examples.texttosql.cli;

import com.github.vertical_blank.sqlformatter.SqlFormatter;

import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.examples.texttosql.tools.LocalTools;
import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Terminal output helpers for the Text-to-SQL CLI.
 *
 * <p>All ANSI colour codes, the progress-spinner logic, and every formatted stdout/stderr print
 * statement live here so that {@link TextToSqlCLI} stays focused on orchestration.
 */
@UtilityClass
@Slf4j
public class ConsoleUtils {

    // -------------------------------------------------------------------------
    // ANSI escape codes
    // -------------------------------------------------------------------------

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String DIM = "\u001B[2m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BRIGHT_GREEN = "\u001B[92m";
    private static final String BRIGHT_YELLOW = "\u001B[93m";
    private static final String BRIGHT_CYAN = "\u001B[96m";

    // -------------------------------------------------------------------------
    // Spinner state
    // -------------------------------------------------------------------------

    /**
     * Global toggle for the progress spinner. {@code true} (the default) means the spinner may be
     * displayed; {@code false} suppresses all spinner output — used while an {@code AskUserTool}
     * interaction is in progress so that the user's terminal is not polluted with spinner lines
     * while they are typing a response.
     */
    private static final AtomicBoolean SPINNER_ENABLED = new AtomicBoolean(true);

    private static final long DEFAULT_FUTURE_TIMEOUT = Long.parseLong(
                                                                      System.getProperty("default.future.timeout.ms",
                                                                                         "5000"));

    /** Fun verbs displayed while the agent is thinking in non-streaming mode. */
    private static final List<String> PROCESSING_VERBS = List.of(
                                                                 "Vaporizing",
                                                                 "Atomizing",
                                                                 "Pulverizing",
                                                                 "Supervising",
                                                                 "Synthesizing",
                                                                 "Quantum-tunneling",
                                                                 "Defragmenting",
                                                                 "Hypercomputing",
                                                                 "Recalibrating",
                                                                 "Triangulating",
                                                                 "Extrapolating",
                                                                 "Turbo-charging",
                                                                 "Galvanizing",
                                                                 "Electrifying",
                                                                 "Catalyzing",
                                                                 "Bootstrapping",
                                                                 "Orchestrating",
                                                                 "Harmonizing",
                                                                 "Contemplating",
                                                                 "Ruminating",
                                                                 "Decimating",
                                                                 "Liquefying",
                                                                 "Disintegrating",
                                                                 "Nebulizing",
                                                                 "Carbonizing",
                                                                 "Combusting",
                                                                 "Evaporating");

    /**
     * Waits for {@code future} to complete, printing a randomly chosen processing verb every 5
     * seconds when {@code showSpinner} is {@code true}.
     *
     * <p>When the future resolves the spinner line is cleared before returning so that subsequent
     * output is not offset by leftover characters.
     *
     * @param <T>         the future's value type
     * @param future      the future to await
     * @param showSpinner {@code true} to display the spinner; pass {@code false} in streaming mode
     *                    where token chunks are already being printed live
     * @return the resolved value of the future
     * @throws InterruptedException if the current thread is interrupted while waiting
     * @throws ExecutionException   if the future completed exceptionally
     */
    @SuppressWarnings("java:S2245")
    public static <T> T awaitWithSpinner(CompletableFuture<T> future, boolean showSpinner)
            throws InterruptedException, ExecutionException {
        while (true) {
            try {
                final T result = future.get(DEFAULT_FUTURE_TIMEOUT, TimeUnit.MILLISECONDS);
                if (showSpinner && SPINNER_ENABLED.get()) {
                    // Erase the spinner line before the caller prints the result.
                    printToStdout("\r" + " ".repeat(80) + "\r");
                }
                return result;
            }
            catch (TimeoutException ignored) {
                if (showSpinner && SPINNER_ENABLED.get()) {
                    final String verb = PROCESSING_VERBS.get(
                                                             // java:S2245 — ThreadLocalRandom is intentional here; this picks
                                                             // a display verb for a CLI spinner animation and has no security
                                                             // or cryptographic purpose whatsoever.
                                                             ThreadLocalRandom.current().nextInt(PROCESSING_VERBS
                                                                     .size()));
                    printToStdout("\r" + BOLD + YELLOW + verb + "..." + RESET);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Spinner vocabulary
    // -------------------------------------------------------------------------

    /**
     * Disables the progress spinner globally. Spinner output will be suppressed until {@link
     * #enableSpinner()} is called.
     */
    public static void disableSpinner() {
        SPINNER_ENABLED.set(false);
    }

    // -------------------------------------------------------------------------
    // Spinner
    // -------------------------------------------------------------------------

    /**
     * Re-enables the progress spinner globally after a previous call to {@link #disableSpinner()}.
     */
    public static void enableSpinner() {
        SPINNER_ENABLED.set(true);
    }

    // -------------------------------------------------------------------------
    // Banner & examples
    // -------------------------------------------------------------------------

    /** Prints the welcome banner in bright cyan. */
    public static void printBanner() {
        printToStdout(BRIGHT_CYAN
                + System.lineSeparator()
                + "╔════════════════════════════════════════════════════╗"
                + System.lineSeparator()
                + "║   Sentinel AI — Text-to-SQL Agent (e-commerce DB)  ║"
                + System.lineSeparator()
                + "╠════════════════════════════════════════════════════╣"
                + System.lineSeparator()
                + "║  Ask questions in plain English about:             ║"
                + System.lineSeparator()
                + "║    • users, sellers, catalog, inventory, orders    ║"
                + System.lineSeparator()
                + "║  Commands:                                         ║"
                + System.lineSeparator()
                + "║    /dumpMessages [file]  — export all messages     ║"
                + System.lineSeparator()
                + "║  Type 'exit' or 'quit' to stop, Ctrl+D to EOF.     ║"
                + System.lineSeparator()
                + "╚════════════════════════════════════════════════════╝"
                + RESET);
    }

    /**
     * Prints a confirmation that the message dump was written successfully.
     *
     * @param path         absolute path of the file that was written
     * @param messageCount number of messages serialized
     */
    public static void printDumpSuccess(String path, int messageCount) {
        printToStdout(BOLD
                + GREEN
                + "[Dump] "
                + RESET
                + GREEN
                + "Exported "
                + messageCount
                + " message(s) → "
                + path
                + RESET
                + System.lineSeparator());
    }

    // -------------------------------------------------------------------------
    // Interactive-loop prompt
    // -------------------------------------------------------------------------

    /**
     * Writes {@code message} as a bold-red {@code [Error]} line to {@link System#err}.
     *
     * @param message the error description
     */
    public static void printError(String message) {
        System.err.println(BOLD + RED + "[Error] " + RESET + RED + message + RESET);
    }

    // -------------------------------------------------------------------------
    // Query result
    // -------------------------------------------------------------------------

    /** Prints a short list of starter prompts to help the user get going. */
    public static void printExamples() {
        printToStdout(CYAN + "Try one of these example prompts to get started:" + RESET + System.lineSeparator());
        printToStdout(System.lineSeparator());
        printToStdout(BOLD + YELLOW + "  1. " + RESET + "List top 3 sellers by order volume" + System.lineSeparator());
        printToStdout(BOLD + YELLOW + "  2. " + RESET + "Find the user with the most number of orders"
                + System.lineSeparator());
        printToStdout(BOLD + YELLOW + "  3. " + RESET + "Find out top cities by shoe sales" + System.lineSeparator());
        printToStdout(BOLD + YELLOW + "  4. " + RESET + "What are the top 5 best-selling products this month?"
                + System.lineSeparator());
        printToStdout(BOLD + YELLOW + "  5. " + RESET + "Show total revenue per product category"
                + System.lineSeparator());
        printToStdout(System.lineSeparator());
    }

    // -------------------------------------------------------------------------
    // Error / warning helpers
    // -------------------------------------------------------------------------

    /** Prints the {@code >} input prompt in bold bright-green. */
    public static void printPrompt() {
        printToStdout(BOLD + BRIGHT_GREEN + "\n> " + RESET);
    }

    /**
     * Renders a {@link SqlQueryResult} in a formatted, colour-highlighted layout:
     *
     * <ul>
     * <li>Cyan box containing the pretty-printed SQL (green text)
     * <li>Dim timing line
     * <li>Bold bright-yellow verbal explanation (when present)
     * <li>ASCII result table
     * </ul>
     *
     * @param result      the query result produced by the agent
     * @param wallClockMs total elapsed time since the query was dispatched
     */
    public static void printStructuredResult(SqlQueryResult result, long wallClockMs) {
        printToStdout(System.lineSeparator());

        // ── Generated SQL ─────────────────────────────────────────────────
        printToStdout(CYAN + "┌─ Generated SQL " + "─".repeat(54) + "┐" + RESET + System.lineSeparator());
        final String formattedSql = formatSql(result.generatedSql());
        for (final String line : formattedSql.split("\n")) {
            printToStdout(GREEN + "│  " + line + RESET + System.lineSeparator());
        }
        printToStdout(CYAN + "└" + "─".repeat(70) + "┘" + RESET + System.lineSeparator());
        printToStdout(System.lineSeparator());

        // ── Timing ────────────────────────────────────────────────────────
        printToStdout(BOLD + BRIGHT_YELLOW + "── Timing Info " + "─".repeat(55) + RESET + System.lineSeparator());
        printToStdout(String.format(DIM + "Query execution: %d ms  │  Wall clock: %d ms" + RESET + "%n",
                                    result.executionTimeMs(),
                                    wallClockMs));
        printToStdout(System.lineSeparator());

        // ── Explanation ───────────────────────────────────────────────────
        if (result.explanation() != null && !result.explanation().isBlank()) {
            printToStdout(BOLD + BRIGHT_YELLOW + "── Verbal Explanation " + "─".repeat(55) + RESET
                    + System.lineSeparator());
            printToStdout(result.explanation() + System.lineSeparator());
            printToStdout(System.lineSeparator());
        }

        // ── Result rows ───────────────────────────────────────────────────
        printToStdout(BOLD + BRIGHT_YELLOW + "── Query Result " + "─".repeat(55) + RESET + System.lineSeparator());
        printToStdout(LocalTools.formatResultsAsTable(result) + System.lineSeparator());
    }

    @SuppressWarnings("java:S106")
    public static void printToStdout(String output) {
        System.out.println(output);
    }

    /**
     * Prints model token-usage statistics for the last query in dim (grey) colour.
     *
     * <p>Emits a single line showing total tokens, request tokens, response tokens, number of tool
     * calls, and number of LLM round-trips. Nothing is printed when {@code usage} is {@code null}.
     *
     * @param usage the {@link ModelUsageStats} returned by {@link
     *              com.phonepe.sentinelai.core.agent.AgentOutput#getUsage()}
     */
    public static void printUsageStats(ModelUsageStats usage) {
        if (usage == null) {
            return;
        }
        printToStdout(BOLD + BRIGHT_YELLOW + "── Usage Stats " + "─".repeat(55) + RESET + System.lineSeparator());
        printToStdout(String.format(DIM
                + "   Total: %d tokens  │  Request: %d  │  Response: %d"
                + "  │  Tool calls: %d  │  LLM requests: %d"
                + RESET
                + "%n",
                                    usage.getTotalTokens(),
                                    usage.getRequestTokens(),
                                    usage.getResponseTokens(),
                                    usage.getToolCallsForRun(),
                                    usage.getRequestsForRun()));

        // ── Request token breakdown ───────────────────────────────────────
        final var req = usage.getRequestTokenDetails();
        printToStdout(String.format(DIM + "   Request breakdown:   cached: %d  │  audio: %d" + RESET + "%n",
                                    req.getCachedTokens(),
                                    req.getAudioTokens()));

        // ── Response token breakdown ──────────────────────────────────────
        final var resp = usage.getResponseTokenDetails();
        printToStdout(String.format(DIM
                + "   Response breakdown:  reasoning: %d  │  accepted predictions: %d"
                + "  │  rejected predictions: %d  │  audio: %d"
                + RESET
                + "%n",
                                    resp.getReasoningTokens(),
                                    resp.getAcceptedPredictionTokens(),
                                    resp.getRejectedPredictionTokens(),
                                    resp.getAudioTokens()));

        printToStdout(System.lineSeparator());
    }

    /**
     * Writes {@code message} as a yellow {@code [Warning]} line to {@link System#out}.
     *
     * @param message the warning description
     */
    public static void printWarning(String message) {
        printToStdout(YELLOW + "[Warning] " + message + RESET + System.lineSeparator());
    }

    public static PrintStream stdout() {
        try {
            return (PrintStream) System.class.getField("out").get(null);
        }
        catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access stdout", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Pretty-prints a SQL string using the sql-formatter library. Falls back to the raw SQL if
     * formatting fails for any reason.
     */
    private static String formatSql(String sql) {
        if (sql == null || sql.isBlank()) {
            return sql;
        }
        try {
            return SqlFormatter.format(sql);
        }
        catch (Exception e) {
            log.debug("SQL formatting failed — using raw SQL: {}", e.getMessage());
            return sql;
        }
    }
}
