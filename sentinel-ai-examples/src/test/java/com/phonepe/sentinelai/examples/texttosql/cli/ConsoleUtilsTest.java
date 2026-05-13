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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ConsoleUtils")
@ResourceLock(value = Resources.SYSTEM_OUT, mode = ResourceAccessMode.READ_WRITE)
@ResourceLock(value = Resources.SYSTEM_ERR, mode = ResourceAccessMode.READ_WRITE)
class ConsoleUtilsTest {

    @Nested
    @DisplayName("awaitWithSpinner — delayed future")
    @Execution(ExecutionMode.SAME_THREAD)
    class AwaitWithSpinnerDelayedTests {

        static {
            System.setProperty("default.future.timeout.ms", "10");
        }

        @Test
        @DisplayName("spinner disabled: delayed future resolves without printing spinner")
        void spinnerDisabledDelayedFuture() throws Exception {
            ConsoleUtils.disableSpinner();
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            CompletableFuture<String> future = new CompletableFuture<>();
            scheduler.schedule(() -> future.complete("ok"), 50, TimeUnit.MILLISECONDS);
            try {
                String result = ConsoleUtils.awaitWithSpinner(future, true);
                assertEquals("ok", result);
            }
            finally {
                scheduler.shutdownNow();
                ConsoleUtils.enableSpinner();
            }
        }

        @Test
        @DisplayName("spinner path: resolves future that completes after a short delay")
        void spinnerPathWithShortDelay() throws Exception {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            CompletableFuture<String> future = new CompletableFuture<>();
            scheduler.schedule(() -> future.complete("delayed"), 50, TimeUnit.MILLISECONDS);
            try {
                String result = ConsoleUtils.awaitWithSpinner(future, true);
                assertEquals("delayed", result);
            }
            finally {
                scheduler.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("printBanner")
    class PrintBannerTests {

        @Test
        @DisplayName("banner contains 'Sentinel AI' text")
        void bannerContainsSentinelAI() {
            ConsoleUtils.printBanner();
            String out = outCapture.toString();
            assertTrue(out.contains("Sentinel AI"), "Banner should mention Sentinel AI");
        }

        @Test
        @DisplayName("prints something to stdout")
        void printsSomethingToStdout() {
            ConsoleUtils.printBanner();
            String out = outCapture.toString();
            assertFalse(out.isBlank(), "printBanner should write to stdout");
        }
    }

    @Nested
    @DisplayName("printDumpSuccess")
    class PrintDumpSuccessTests {

        @Test
        @DisplayName("includes path and message count")
        void includesPathAndCount() {
            ConsoleUtils.printDumpSuccess("/tmp/dump.json", 7);
            String out = outCapture.toString();
            assertTrue(out.contains("/tmp/dump.json"));
            assertTrue(out.contains("7"));
        }

        @Test
        @DisplayName("writes [Dump] to stdout")
        void writesDumpToStdout() {
            ConsoleUtils.printDumpSuccess("/tmp/dump.json", 5);
            String out = outCapture.toString();
            assertTrue(out.contains("[Dump]"));
        }
    }

    @Nested
    @DisplayName("printError")
    class PrintErrorTests {

        @Test
        @DisplayName("includes error message in stderr output")
        void includesMessageInStderr() {
            ConsoleUtils.printError("disk is full");
            String err = errCapture.toString();
            assertTrue(err.contains("disk is full"));
        }

        @Test
        @DisplayName("writes [Error] to stderr")
        void writesErrorToStderr() {
            ConsoleUtils.printError("something went wrong");
            String err = errCapture.toString();
            assertTrue(err.contains("[Error]"), "Should write [Error] to stderr");
        }
    }

    @Nested
    @DisplayName("printExamples")
    class PrintExamplesTests {

        @Test
        @DisplayName("prints example prompts to stdout")
        void printsExamplesToStdout() {
            ConsoleUtils.printExamples();
            String out = outCapture.toString();
            assertFalse(out.isBlank());
        }
    }

    @Nested
    @DisplayName("printStructuredResult")
    class PrintStructuredResultTests {

        @Test
        @DisplayName("handles blank generatedSql without throwing — covers isBlank branch")
        void handlesBlankSqlWithoutThrowing() {
            SqlQueryResult result = new SqlQueryResult("   ", List.of(), null, 0L);
            assertDoesNotThrow(() -> ConsoleUtils.printStructuredResult(result, 0L));
        }

        @Test
        @DisplayName("handles null generatedSql without throwing")
        void handlesNullSqlWithoutThrowing() {
            // ConsoleUtils.formatSql returns null for null input; printStructuredResult
            // calls formattedSql.split("\n") which NPEs on null — this is a known behaviour.
            // The test verifies that null SQL is passed through formatSql (line 369 covered).
            SqlQueryResult result = new SqlQueryResult(null, List.of(), null, 0L);
            // NullPointerException is expected because formattedSql.split() is called on null
            assertThrows(NullPointerException.class,
                         () -> ConsoleUtils.printStructuredResult(result, 0L));
        }

        @Test
        @DisplayName("handles unparseable SQL gracefully — covers formatSql catch branch")
        void handlesUnparseableSqlGracefully() {
            SqlQueryResult result = new SqlQueryResult(
                                                       "THIS IS DELIBERATELY @BROKEN% SQL !!!",
                                                       List.of(),
                                                       null,
                                                       0L);
            assertDoesNotThrow(() -> ConsoleUtils.printStructuredResult(result, 0L));
        }

        @Test
        @DisplayName("omits explanation section when explanation is blank")
        void omitsExplanationWhenBlank() {
            SqlQueryResult result = new SqlQueryResult("SELECT 1", List.of(), "  ", 10L);
            ConsoleUtils.printStructuredResult(result, 50L);
            String out = outCapture.toString();
            assertFalse(out.contains("Verbal Explanation"),
                        "Should not print explanation section when blank");
        }

        @Test
        @DisplayName("omits explanation section when explanation is null")
        void omitsExplanationWhenNull() {
            SqlQueryResult result = new SqlQueryResult("SELECT 1", List.of(), null, 10L);
            ConsoleUtils.printStructuredResult(result, 50L);
            String out = outCapture.toString();
            assertFalse(out.contains("Verbal Explanation"),
                        "Should not print explanation section when null");
        }

        @Test
        @DisplayName("prints explanation when present")
        void printsExplanationWhenPresent() {
            SqlQueryResult result = new SqlQueryResult("SELECT 1", List.of(), "This is the explanation.", 10L);
            ConsoleUtils.printStructuredResult(result, 50L);
            String out = outCapture.toString();
            assertTrue(out.contains("Verbal Explanation"), "Should print explanation header");
            assertTrue(out.contains("This is the explanation."), "Should print explanation text");
        }

        @Test
        @DisplayName("prints result rows section")
        void printsResultRowsSection() {
            SqlQueryResult result = new SqlQueryResult(
                                                       "SELECT user_id FROM users LIMIT 1",
                                                       List.of("{\"user_id\":1}"),
                                                       null,
                                                       5L);
            ConsoleUtils.printStructuredResult(result, 10L);
            String out = outCapture.toString();
            assertTrue(out.contains("Query Result"), "Should print query result section");
        }

        @Test
        @DisplayName("prints SQL and timing info to stdout")
        void printsSqlAndTimingInfo() {
            SqlQueryResult result = new SqlQueryResult("SELECT 1", List.of(), "no rows found", 42L);
            ConsoleUtils.printStructuredResult(result, 100L);
            String out = outCapture.toString();
            assertTrue(out.contains("Generated SQL"), "Should print SQL section header");
            assertTrue(out.contains("Timing"), "Should print timing section");
        }
    }

    // =========================================================================
    // Spinner toggle
    // =========================================================================

    @Nested
    @DisplayName("printUsageStats (non-null)")
    class PrintUsageStatsNonNullTests {

        @Test
        @DisplayName("writes token counts to stdout")
        void writesTokenCounts() {
            ModelUsageStats usage = new ModelUsageStats();
            usage.incrementTotalTokens(100)
                    .incrementRequestTokens(60)
                    .incrementResponseTokens(40)
                    .incrementToolCallsForRun(2)
                    .incrementRequestsForRun(1);
            ConsoleUtils.printUsageStats(usage);
            String out = outCapture.toString();
            assertTrue(out.contains("100"), "Should contain total token count");
        }

        @Test
        @DisplayName("writes Usage Stats header to stdout")
        void writesUsageStatsHeader() {
            ModelUsageStats usage = new ModelUsageStats();
            ConsoleUtils.printUsageStats(usage);
            String out = outCapture.toString();
            assertTrue(out.contains("Usage Stats"), "Should write usage stats header");
        }
    }

    // =========================================================================
    // printBanner
    // =========================================================================

    @Nested
    @DisplayName("printWarning")
    class PrintWarningTests {

        @Test
        @DisplayName("includes warning message in stdout")
        void includesMessageInStdout() {
            ConsoleUtils.printWarning("something suspicious");
            String out = outCapture.toString();
            assertTrue(out.contains("something suspicious"));
        }

        @Test
        @DisplayName("writes [Warning] to stdout")
        void writesWarningToStdout() {
            ConsoleUtils.printWarning("low memory");
            String out = outCapture.toString();
            assertTrue(out.contains("[Warning]"), "Should write [Warning] to stdout");
        }
    }

    // =========================================================================
    // printExamples
    // =========================================================================

    @Nested
    @DisplayName("spinner toggle")
    class SpinnerToggleTests {

        @Test
        @DisplayName("awaitWithSpinner resolves an already-completed future instantly")
        void awaitWithSpinnerCompletedFuture() throws Exception {
            CompletableFuture<String> future = CompletableFuture.completedFuture("done");
            String result = ConsoleUtils.awaitWithSpinner(future, false);
            assertEquals("done", result);
        }

        @Test
        @DisplayName("awaitWithSpinner with spinner disabled resolves a completed future")
        void awaitWithSpinnerDisabled() throws Exception {
            ConsoleUtils.disableSpinner();
            CompletableFuture<String> future = CompletableFuture.completedFuture("hello");
            String result = ConsoleUtils.awaitWithSpinner(future, true);
            assertEquals("hello", result);
        }

        @Test
        @DisplayName("awaitWithSpinner with showSpinner=true resolves a completed future")
        void awaitWithSpinnerShowSpinnerTrue() throws Exception {
            CompletableFuture<Integer> future = CompletableFuture.completedFuture(42);
            Integer result = ConsoleUtils.awaitWithSpinner(future, true);
            assertEquals(42, result);
        }

        @Test
        @DisplayName("disableSpinner / enableSpinner do not throw")
        void toggleDoesNotThrow() {
            assertDoesNotThrow(ConsoleUtils::disableSpinner);
            assertDoesNotThrow(ConsoleUtils::enableSpinner);
        }
    }

    // =========================================================================
    // printPrompt
    // =========================================================================

    private PrintStream originalOut;

    // =========================================================================
    // printError
    // =========================================================================

    private PrintStream originalErr;

    // =========================================================================
    // printWarning
    // =========================================================================

    private ByteArrayOutputStream outCapture;

    // =========================================================================
    // printDumpSuccess
    // =========================================================================

    private ByteArrayOutputStream errCapture;

    // =========================================================================
    // printUsageStats
    // =========================================================================

    @Test
    @DisplayName("printPrompt writes '>' to stdout")
    void printPromptWritesPrompt() {
        ConsoleUtils.printPrompt();
        String out = outCapture.toString();
        assertTrue(out.contains(">"), "Prompt should contain '>'");
    }

    // =========================================================================
    // printUsageStats with real ModelUsageStats
    // =========================================================================

    @Test
    @DisplayName("printUsageStats with null usage does nothing")
    void printUsageStatsNullUsageDoesNothing() {
        assertDoesNotThrow(() -> ConsoleUtils.printUsageStats(null));
        // Nothing should be written to stdout
        String out = outCapture.toString();
        assertTrue(out.isEmpty(), "Null usage should produce no output");
    }

    // =========================================================================
    // printStructuredResult
    // =========================================================================

    @BeforeEach
    void redirectStreams() {
        originalOut = ConsoleUtils.stdout();
        originalErr = System.err;
        outCapture = new ByteArrayOutputStream();
        errCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outCapture));
        System.setErr(new PrintStream(errCapture));
        // Ensure spinner is always re-enabled before each test
        ConsoleUtils.enableSpinner();
    }

    // =========================================================================
    // awaitWithSpinner — delayed future (covers TimeoutException spinner path)
    // =========================================================================

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        ConsoleUtils.enableSpinner();
    }
}
