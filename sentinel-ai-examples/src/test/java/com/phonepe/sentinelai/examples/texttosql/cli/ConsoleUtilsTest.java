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

import static org.junit.jupiter.api.Assertions.*;

import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.examples.texttosql.tools.model.SqlQueryResult;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("ConsoleUtils")
class ConsoleUtilsTest {

    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outCapture;
    private ByteArrayOutputStream errCapture;

    @BeforeEach
    void redirectStreams() {
        originalOut = System.out;
        originalErr = System.err;
        outCapture = new ByteArrayOutputStream();
        errCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outCapture));
        System.setErr(new PrintStream(errCapture));
        // Ensure spinner is always re-enabled before each test
        ConsoleUtils.enableSpinner();
    }

    @AfterEach
    void restoreStreams() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        ConsoleUtils.enableSpinner();
    }

    // =========================================================================
    // Spinner toggle
    // =========================================================================

    @Nested
    @DisplayName("spinner toggle")
    class SpinnerToggleTests {

        @Test
        @DisplayName("disableSpinner / enableSpinner do not throw")
        void toggleDoesNotThrow() {
            assertDoesNotThrow(ConsoleUtils::disableSpinner);
            assertDoesNotThrow(ConsoleUtils::enableSpinner);
        }

        @Test
        @DisplayName("awaitWithSpinner resolves an already-completed future instantly")
        void awaitWithSpinnerCompletedFuture() throws Exception {
            CompletableFuture<String> future = CompletableFuture.completedFuture("done");
            String result = ConsoleUtils.awaitWithSpinner(future, false);
            assertEquals("done", result);
        }

        @Test
        @DisplayName("awaitWithSpinner with showSpinner=true resolves a completed future")
        void awaitWithSpinnerShowSpinnerTrue() throws Exception {
            CompletableFuture<Integer> future = CompletableFuture.completedFuture(42);
            Integer result = ConsoleUtils.awaitWithSpinner(future, true);
            assertEquals(42, result);
        }

        @Test
        @DisplayName("awaitWithSpinner with spinner disabled resolves a completed future")
        void awaitWithSpinnerDisabled() throws Exception {
            ConsoleUtils.disableSpinner();
            CompletableFuture<String> future = CompletableFuture.completedFuture("hello");
            String result = ConsoleUtils.awaitWithSpinner(future, true);
            assertEquals("hello", result);
        }
    }

    // =========================================================================
    // printBanner
    // =========================================================================

    @Nested
    @DisplayName("printBanner")
    class PrintBannerTests {

        @Test
        @DisplayName("prints something to stdout")
        void printsSomethingToStdout() {
            ConsoleUtils.printBanner();
            String out = outCapture.toString();
            assertFalse(out.isBlank(), "printBanner should write to stdout");
        }

        @Test
        @DisplayName("banner contains 'Sentinel AI' text")
        void bannerContainsSentinelAI() {
            ConsoleUtils.printBanner();
            String out = outCapture.toString();
            assertTrue(out.contains("Sentinel AI"), "Banner should mention Sentinel AI");
        }
    }

    // =========================================================================
    // printExamples
    // =========================================================================

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

    // =========================================================================
    // printPrompt
    // =========================================================================

    @Test
    @DisplayName("printPrompt writes '>' to stdout")
    void printPromptWritesPrompt() {
        ConsoleUtils.printPrompt();
        String out = outCapture.toString();
        assertTrue(out.contains(">"), "Prompt should contain '>'");
    }

    // =========================================================================
    // printError
    // =========================================================================

    @Nested
    @DisplayName("printError")
    class PrintErrorTests {

        @Test
        @DisplayName("writes [Error] to stderr")
        void writesErrorToStderr() {
            ConsoleUtils.printError("something went wrong");
            String err = errCapture.toString();
            assertTrue(err.contains("[Error]"), "Should write [Error] to stderr");
        }

        @Test
        @DisplayName("includes error message in stderr output")
        void includesMessageInStderr() {
            ConsoleUtils.printError("disk is full");
            String err = errCapture.toString();
            assertTrue(err.contains("disk is full"));
        }
    }

    // =========================================================================
    // printWarning
    // =========================================================================

    @Nested
    @DisplayName("printWarning")
    class PrintWarningTests {

        @Test
        @DisplayName("writes [Warning] to stdout")
        void writesWarningToStdout() {
            ConsoleUtils.printWarning("low memory");
            String out = outCapture.toString();
            assertTrue(out.contains("[Warning]"), "Should write [Warning] to stdout");
        }

        @Test
        @DisplayName("includes warning message in stdout")
        void includesMessageInStdout() {
            ConsoleUtils.printWarning("something suspicious");
            String out = outCapture.toString();
            assertTrue(out.contains("something suspicious"));
        }
    }

    // =========================================================================
    // printDumpSuccess
    // =========================================================================

    @Nested
    @DisplayName("printDumpSuccess")
    class PrintDumpSuccessTests {

        @Test
        @DisplayName("writes [Dump] to stdout")
        void writesDumpToStdout() {
            ConsoleUtils.printDumpSuccess("/tmp/dump.json", 5);
            String out = outCapture.toString();
            assertTrue(out.contains("[Dump]"));
        }

        @Test
        @DisplayName("includes path and message count")
        void includesPathAndCount() {
            ConsoleUtils.printDumpSuccess("/tmp/dump.json", 7);
            String out = outCapture.toString();
            assertTrue(out.contains("/tmp/dump.json"));
            assertTrue(out.contains("7"));
        }
    }

    // =========================================================================
    // printUsageStats
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
    // printUsageStats with real ModelUsageStats
    // =========================================================================

    @Nested
    @DisplayName("printUsageStats (non-null)")
    class PrintUsageStatsNonNullTests {

        @Test
        @DisplayName("writes Usage Stats header to stdout")
        void writesUsageStatsHeader() {
            ModelUsageStats usage = new ModelUsageStats();
            ConsoleUtils.printUsageStats(usage);
            String out = outCapture.toString();
            assertTrue(out.contains("Usage Stats"), "Should write usage stats header");
        }

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
    }

    // =========================================================================
    // printStructuredResult
    // =========================================================================

    @Nested
    @DisplayName("printStructuredResult")
    class PrintStructuredResultTests {

        @Test
        @DisplayName("prints SQL and timing info to stdout")
        void printsSqlAndTimingInfo() {
            SqlQueryResult result =
                    new SqlQueryResult("SELECT 1", List.of(), "no rows found", 42L);
            ConsoleUtils.printStructuredResult(result, 100L);
            String out = outCapture.toString();
            assertTrue(out.contains("Generated SQL"), "Should print SQL section header");
            assertTrue(out.contains("Timing"), "Should print timing section");
        }

        @Test
        @DisplayName("prints explanation when present")
        void printsExplanationWhenPresent() {
            SqlQueryResult result =
                    new SqlQueryResult("SELECT 1", List.of(), "This is the explanation.", 10L);
            ConsoleUtils.printStructuredResult(result, 50L);
            String out = outCapture.toString();
            assertTrue(out.contains("Verbal Explanation"), "Should print explanation header");
            assertTrue(out.contains("This is the explanation."), "Should print explanation text");
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
        @DisplayName("omits explanation section when explanation is blank")
        void omitsExplanationWhenBlank() {
            SqlQueryResult result = new SqlQueryResult("SELECT 1", List.of(), "  ", 10L);
            ConsoleUtils.printStructuredResult(result, 50L);
            String out = outCapture.toString();
            assertFalse(out.contains("Verbal Explanation"),
                    "Should not print explanation section when blank");
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
    }
}
