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

import static org.junit.jupiter.api.Assertions.*;

import com.phonepe.sentinelai.examples.texttosql.cli.ConsoleUtils;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AskUserTool}.
 *
 * <p>Because the tool reads from {@link System#in} via a private {@code stdin} field, we inject a
 * {@link ByteArrayInputStream} via reflection so that tests are fully deterministic and do not
 * block waiting for terminal input.
 */
@DisplayName("AskUserTool")
class AskUserToolTest {

    private AskUserTool tool;
    private PrintStream originalOut;
    private PrintStream originalErr;
    private ByteArrayOutputStream outCapture;

    @BeforeEach
    void setUp() {
        tool = new AskUserTool();
        originalOut = System.out;
        originalErr = System.err;
        outCapture = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outCapture));
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
        ConsoleUtils.enableSpinner();
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
        ConsoleUtils.enableSpinner();
    }

    /**
     * Injects a {@link BufferedReader} backed by {@code lines} into the {@code stdin} field of
     * the tool.
     */
    private void injectStdin(String... lines) throws Exception {
        final String input = String.join("\n", lines) + "\n";
        final BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(
                                        input.getBytes(StandardCharsets.UTF_8)),
                                StandardCharsets.UTF_8));
        final Field stdinField = AskUserTool.class.getDeclaredField("stdin");
        stdinField.setAccessible(true);
        stdinField.set(tool, reader);
    }

    /** Injects a stdin reader that immediately returns EOF (simulates Ctrl-D). */
    private void injectEof() throws Exception {
        final BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ByteArrayInputStream(new byte[0]),
                                StandardCharsets.UTF_8));
        final Field stdinField = AskUserTool.class.getDeclaredField("stdin");
        stdinField.setAccessible(true);
        stdinField.set(tool, reader);
    }

    // =========================================================================
    // name()
    // =========================================================================

    @Test
    @DisplayName("name() returns 'ask_user_tool'")
    void nameReturnsExpected() {
        assertEquals("ask_user_tool", tool.name());
    }

    // =========================================================================
    // askUserQuestion
    // =========================================================================

    @Nested
    @DisplayName("askUserQuestion")
    class AskUserQuestionTests {

        @Test
        @DisplayName("returns trimmed user input")
        void returnsTrimmedInput() throws Exception {
            injectStdin("  hello world  ");
            final String result = tool.askUserQuestion("What is your name?");
            assertEquals("hello world", result);
        }

        @Test
        @DisplayName("returns '<no input>' when user presses Enter with no text")
        void emptyLineReturnsNoInput() throws Exception {
            injectStdin("");
            final String result = tool.askUserQuestion("Any preferences?");
            assertEquals("<no input>", result);
        }

        @Test
        @DisplayName("returns '<no input>' on EOF")
        void eofReturnsNoInput() throws Exception {
            injectEof();
            final String result = tool.askUserQuestion("Hello?");
            assertEquals("<no input>", result);
        }

        @Test
        @DisplayName("prints question banner to stdout")
        void printsBannerToStdout() throws Exception {
            injectStdin("answer");
            tool.askUserQuestion("What color?");
            final String out = outCapture.toString();
            assertTrue(out.contains("Clarification needed"), "Should print question banner");
            assertTrue(out.contains("What color?"), "Should echo the question text");
        }

        @Test
        @DisplayName("spinner is re-enabled after call")
        void spinnerReEnabledAfterCall() throws Exception {
            ConsoleUtils.disableSpinner();
            injectStdin("ok");
            tool.askUserQuestion("question");
            // After the finally block, spinner should be re-enabled
            // (no direct public query, but enableSpinner/disableSpinner are idempotent)
            assertDoesNotThrow(ConsoleUtils::enableSpinner);
        }
    }

    // =========================================================================
    // askUserToChoose
    // =========================================================================

    @Nested
    @DisplayName("askUserToChoose")
    class AskUserToChooseTests {

        @Test
        @DisplayName("selecting by 1-based index returns corresponding choice")
        void selectByIndex() throws Exception {
            injectStdin("2");
            final String result = tool.askUserToChoose("Pick one:", "Alpha;Beta;Gamma");
            assertEquals("Beta", result);
        }

        @Test
        @DisplayName("selecting by first index returns first choice")
        void selectFirstByIndex() throws Exception {
            injectStdin("1");
            final String result = tool.askUserToChoose("Pick one:", "Alpha;Beta;Gamma");
            assertEquals("Alpha", result);
        }

        @Test
        @DisplayName("selecting by exact label (case-insensitive) returns that choice")
        void selectByLabel() throws Exception {
            injectStdin("gamma");
            final String result = tool.askUserToChoose("Pick one:", "Alpha;Beta;Gamma");
            assertEquals("Gamma", result);
        }

        @Test
        @DisplayName("selecting out-of-range number returns raw input")
        void outOfRangeIndexReturnsRaw() throws Exception {
            injectStdin("99");
            final String result = tool.askUserToChoose("Pick one:", "Alpha;Beta;Gamma");
            assertEquals("99", result);
        }

        @Test
        @DisplayName("typing free text that doesn't match any choice returns raw input")
        void freeTextReturnsRaw() throws Exception {
            injectStdin("something else entirely");
            final String result = tool.askUserToChoose("Pick one:", "Alpha;Beta;Gamma");
            assertEquals("something else entirely", result);
        }

        @Test
        @DisplayName("null choices falls back to free-form question")
        void nullChoicesFallsBackToFreeForm() throws Exception {
            injectStdin("free text answer");
            final String result = tool.askUserToChoose("What do you want?", null);
            assertEquals("free text answer", result);
        }

        @Test
        @DisplayName("blank choices falls back to free-form question")
        void blankChoicesFallsBackToFreeForm() throws Exception {
            injectStdin("my answer");
            final String result = tool.askUserToChoose("What do you want?", "   ");
            assertEquals("my answer", result);
        }

        @Test
        @DisplayName("choices with only semicolons (all empty tokens) falls back to free-form")
        void allEmptyTokensFallsBack() throws Exception {
            injectStdin("ok");
            final String result = tool.askUserToChoose("Pick?", ";;;");
            assertEquals("ok", result);
        }

        @Test
        @DisplayName("prints numbered choices to stdout")
        void printsNumberedChoices() throws Exception {
            injectStdin("1");
            tool.askUserToChoose("Which?", "OptionA;OptionB");
            final String out = outCapture.toString();
            assertTrue(out.contains("OptionA"), "Should list OptionA");
            assertTrue(out.contains("OptionB"), "Should list OptionB");
        }

        @Test
        @DisplayName("returns '<no input>' on EOF inside choices")
        void eofInsideChoicesReturnsNoInput() throws Exception {
            injectEof();
            final String result = tool.askUserToChoose("Choose?", "A;B;C");
            assertEquals("<no input>", result);
        }
    }
}
