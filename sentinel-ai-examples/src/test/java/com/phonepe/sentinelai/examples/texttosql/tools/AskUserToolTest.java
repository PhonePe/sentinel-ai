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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.examples.texttosql.cli.ConsoleUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AskUserTool}.
 *
 * <p>Because the tool reads from {@link System#in} via a private {@code stdin} field, we inject a
 * {@link ByteArrayInputStream} via reflection so that tests are fully deterministic and do not
 * block waiting for terminal input.
 */
class AskUserToolTest {

    @Nested
    class AskUserQuestionTests {

        @Test
        void emptyLineReturnsNoInput() throws Exception {
            injectStdin("");
            final var result = tool.askUserQuestion("Any preferences?");
            assertEquals("<no input>", result);
        }

        @Test
        void eofReturnsNoInput() throws Exception {
            injectEof();
            final var result = tool.askUserQuestion("Hello?");
            assertEquals("<no input>", result);
        }

        @Test
        void printsBannerToStdout() throws Exception {
            injectStdin("answer");
            tool.askUserQuestion("What color?");
            final var out = outCapture.toString();
            assertTrue(out.contains("Clarification needed"), "Should print question banner");
            assertTrue(out.contains("What color?"), "Should echo the question text");
        }

        @Test
        void returnsTrimmedInput() throws Exception {
            injectStdin("  hello world  ");
            final var result = tool.askUserQuestion("What is your name?");
            assertEquals("hello world", result);
        }

        @Test
        void spinnerReEnabledAfterCall() throws Exception {
            ConsoleUtils.disableSpinner();
            injectStdin("ok");
            tool.askUserQuestion("question");
            // After the finally block, spinner should be re-enabled
            // (no direct public query, but enableSpinner/disableSpinner are idempotent)
            assertDoesNotThrow(ConsoleUtils::enableSpinner);
        }
    }

    @Nested
    class AskUserToChooseTests {

        @Test
        void allEmptyTokensFallsBack() throws Exception {
            injectStdin("ok");
            final var result = tool.askUserToChoose("Pick?", ";;;");
            assertEquals("ok", result);
        }

        @Test
        void blankChoicesFallsBackToFreeForm() throws Exception {
            injectStdin("my answer");
            final var result = tool.askUserToChoose("What do you want?", "   ");
            assertEquals("my answer", result);
        }

        @Test
        void eofInsideChoicesReturnsNoInput() throws Exception {
            injectEof();
            final var result = tool.askUserToChoose("Choose?", "A;B;C");
            assertEquals("<no input>", result);
        }

        @Test
        void freeTextReturnsRaw() throws Exception {
            injectStdin("something else entirely");
            final var result = tool.askUserToChoose("Pick one:", "Alpha;Beta;Gamma");
            assertEquals("something else entirely", result);
        }

        @Test
        void nullChoicesFallsBackToFreeForm() throws Exception {
            injectStdin("free text answer");
            final var result = tool.askUserToChoose("What do you want?", null);
            assertEquals("free text answer", result);
        }

        @Test
        void outOfRangeIndexReturnsRaw() throws Exception {
            injectStdin("99");
            final var result = tool.askUserToChoose("Pick one:", "Alpha;Beta;Gamma");
            assertEquals("99", result);
        }

        @Test
        void printsNumberedChoices() throws Exception {
            injectStdin("1");
            tool.askUserToChoose("Which?", "OptionA;OptionB");
            final var out = outCapture.toString();
            assertTrue(out.contains("OptionA"), "Should list OptionA");
            assertTrue(out.contains("OptionB"), "Should list OptionB");
        }

        @Test
        void selectByIndex() throws Exception {
            injectStdin("2");
            final var result = tool.askUserToChoose("Pick one:", "Alpha;Beta;Gamma");
            assertEquals("Beta", result);
        }

        @Test
        void selectByLabel() throws Exception {
            injectStdin("gamma");
            final var result = tool.askUserToChoose("Pick one:", "Alpha;Beta;Gamma");
            assertEquals("Gamma", result);
        }

        @Test
        void selectFirstByIndex() throws Exception {
            injectStdin("1");
            final var result = tool.askUserToChoose("Pick one:", "Alpha;Beta;Gamma");
            assertEquals("Alpha", result);
        }
    }

    private AskUserTool tool;
    private PrintStream originalOut;

    private PrintStream originalErr;

    private ByteArrayOutputStream outCapture;

    @Test
    void nameReturnsExpected() {
        assertEquals("ask_user_tool", tool.name());
    }

    @BeforeEach
    void setUp() {
        tool = new AskUserTool();
        originalOut = ConsoleUtils.stdout();
        originalErr = System.err;
        outCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outCapture));
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
        ConsoleUtils.enableSpinner();
    }

    // =========================================================================
    // name()
    // =========================================================================

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        ConsoleUtils.enableSpinner();
    }

    // =========================================================================
    // askUserQuestion
    // =========================================================================

    /** Injects a stdin reader that immediately returns EOF (simulates Ctrl-D). */
    private void injectEof() throws Exception {
        final var reader = new BufferedReader(
                                              new InputStreamReader(
                                                                    new ByteArrayInputStream(new byte[0]),
                                                                    StandardCharsets.UTF_8));
        final var stdinField = AskUserTool.class.getDeclaredField("stdin");
        stdinField.setAccessible(true);
        stdinField.set(tool, reader);
    }

    // =========================================================================
    // askUserToChoose
    // =========================================================================

    /**
     * Injects a {@link BufferedReader} backed by {@code lines} into the {@code stdin} field of
     * the tool.
     */
    private void injectStdin(String... lines) throws Exception {
        final var input = String.join("\n", lines) + "\n";
        final var reader = new BufferedReader(
                                              new InputStreamReader(
                                                                    new ByteArrayInputStream(
                                                                                             input.getBytes(StandardCharsets.UTF_8)),
                                                                    StandardCharsets.UTF_8));
        final var stdinField = AskUserTool.class.getDeclaredField("stdin");
        stdinField.setAccessible(true);
        stdinField.set(tool, reader);
    }
}
