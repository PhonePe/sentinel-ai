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

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;
import com.phonepe.sentinelai.examples.texttosql.cli.ConsoleUtils;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * A {@link ToolBox} that lets the LLM pause and ask the human operator for clarification.
 *
 * <p>Two tools are exposed:
 *
 * <ul>
 *   <li>{@code ask_user_question} — for open-ended questions where any free-text answer is valid.
 *   <li>{@code ask_user_to_choose} — for situations where there are a fixed set of discrete
 *       choices; the LLM supplies the option labels and the user picks one.
 * </ul>
 *
 * <p>Both tools block the current thread until the user presses Enter, making the call
 * synchronous from the agent's perspective. The user's input is returned verbatim as the tool
 * result and fed back into the model as the tool call response.
 *
 * <p>Register an instance with the agent via:
 *
 * <pre>{@code
 * agent.registerToolbox(new AskUserTool());
 * }</pre>
 */
@Slf4j
public class AskUserTool implements ToolBox {

    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_BRIGHT_YELLOW = "\u001B[93m";
    private static final String ANSI_BRIGHT_GREEN = "\u001B[92m";

    /** Shared stdin reader — reused across calls so the stream is never closed prematurely. */
    private final BufferedReader stdin =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    @Override
    public String name() {
        return "ask_user_tool";
    }

    // -------------------------------------------------------------------------
    // Tools
    // -------------------------------------------------------------------------

    /**
     * Asks the user a free-form question and blocks until they enter a response.
     *
     * <p>Use this tool whenever the user's intent is ambiguous and a free-text answer is most
     * appropriate — for example when you need to know a preference, a missing value, or any
     * information that cannot be expressed as a multiple-choice selection.
     *
     * @param question the question to display to the user
     * @return the raw text the user typed, or {@code "<no input>"} if EOF was reached
     */
    @Tool(
            name = "ask_user_question",
            timeoutSeconds = -1,
            value =
                    """
                            Ask the user a free-form clarification question and wait for their answer. 
                            Use this when the user's intent is ambiguous or a required piece of information 
                            is missing and cannot be expressed as a list of fixed choices. 
                            The user's typed response is returned verbatim.
                    """)
    public String askUserQuestion(String question) {
        ConsoleUtils.disableSpinner();
        try {
            printQuestion(question);
            return readUserInput();
        } finally {
            ConsoleUtils.enableSpinner();
        }
    }

    /**
     * Presents the user with a numbered list of discrete choices and blocks until they select one.
     *
     * <p>Use this tool when there are multiple valid interpretations or execution paths and you
     * want the user to pick one explicitly — for example when a query could target different time
     * ranges, different aggregation levels, or different related tables.
     *
     * @param question the question or context explaining what is being decided
     * @param choices a semicolon-separated string of option labels to present (must contain at
     *     least two items, e.g. {@code "Option A;Option B;Option C"})
     * @return the label of the chosen option, exactly as supplied in {@code choices}, or the raw
     *     input if the user typed something not matching any option number or label
     */
    @Tool(
            name = "ask_user_to_choose",
            timeoutSeconds = -1,
            value =
                    """
                            Present the user with a numbered list of discrete choices and wait for them to pick one.
                            Use this when there are multiple valid interpretations or alternative execution paths — for example
                            different time ranges, different tables or fields, grouping keys, or alternative queries —
                            and you need the user to explicitly select one of the many possible choices.
                            The choices must be provided as a single semicolon-separated string, e.g. "Option A;Option B;Option C".
                            Returns the label of the selected option (1-based index).
                            For example, if there are three choices for a question, then
                            1. <First choice>; 2. <Second choice>; 3. <Third choice>
                            Note that because ';' (semi-colon) is used as a choice delimiter, it can't be used in the choice descriptions
                    """)
    public String askUserToChoose(
            @JsonPropertyDescription("The question to ask the user")
            String question,
            @JsonPropertyDescription(
                    "The answer choices as a semicolon-separated string, e.g. \"Option A;Option B;Option C\"."
                            + " Each token becomes one numbered option presented to the user.")
            String choices) {
        ConsoleUtils.disableSpinner();
        try {
            if (choices == null || choices.isBlank()) {
                log.warn("askUserToChoose called with no choices — falling back to free-form question");
                return askUserQuestion(question);
            }

            final List<String> choiceList =
                    Arrays.stream(choices.split(";"))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();

            if (choiceList.isEmpty()) {
                log.warn("askUserToChoose: no non-empty tokens after parsing '{}' — falling back to free-form question", choices);
                return askUserQuestion(question);
            }

            printChoices(question, choiceList);
            final String raw = readUserInput();

            // Try to interpret the input as a 1-based index first.
            try {
                final int idx = Integer.parseInt(raw.trim());
                if (idx >= 1 && idx <= choiceList.size()) {
                    final String selected = choiceList.get(idx - 1);
                    log.debug("User selected choice #{}: {}", idx, selected);
                    return selected;
                }
            } catch (NumberFormatException ignored) {
                // Not a number — fall through and return the raw text.
            }

            // Check if the raw input matches one of the choice labels (case-insensitive).
            for (final String choice : choiceList) {
                if (choice.equalsIgnoreCase(raw.trim())) {
                    log.debug("User matched choice by label: {}", choice);
                    return choice;
                }
            }

            // Return whatever the user typed; the LLM can interpret it.
            log.debug("User input '{}' did not match any choice — returning raw input", raw);
            return raw;
        } finally {
            ConsoleUtils.enableSpinner();
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /** Prints a styled question banner to stdout and the {@code >} prompt. */
    private static void printQuestion(String question) {
        System.out.println();
        System.out.println(
                ANSI_BOLD
                        + ANSI_CYAN
                        + "┌─ Clarification needed "
                        + "─".repeat(47)
                        + "┐"
                        + ANSI_RESET);
        System.out.println(ANSI_BRIGHT_YELLOW + "  " + question + ANSI_RESET);
        System.out.println(ANSI_BOLD + ANSI_CYAN + "└" + "─".repeat(70) + "┘" + ANSI_RESET);
        printInputPrompt();
    }

    /** Prints a styled multiple-choice menu to stdout and the {@code >} prompt. */
    private static void printChoices(String question, List<String> choices) {
        System.out.println();
        System.out.println(
                ANSI_BOLD
                        + ANSI_CYAN
                        + "┌─ Please choose one of the following "
                        + "─".repeat(33)
                        + "┐"
                        + ANSI_RESET);
        System.out.println(ANSI_BRIGHT_YELLOW + "  " + question + ANSI_RESET);
        System.out.println();
        for (int i = 0; i < choices.size(); i++) {
            System.out.printf(
                    "  %s%s%d.%s %s%n",
                    ANSI_BOLD,
                    ANSI_BRIGHT_YELLOW,
                    i + 1,
                    ANSI_RESET,
                    choices.get(i));
        }
        System.out.println(ANSI_BOLD + ANSI_CYAN + "└" + "─".repeat(70) + "┘" + ANSI_RESET);
        System.out.println("  Enter a number (1–" + choices.size() + ") or type your answer:");
        printInputPrompt();
    }

    /** Prints the {@code >} input prompt. */
    private static void printInputPrompt() {
        System.out.print(ANSI_BOLD + ANSI_BRIGHT_GREEN + "> " + ANSI_RESET);
        System.out.flush();
    }

    /**
     * Reads one line from stdin.
     *
     * @return the trimmed line, or {@code "<no input>"} on EOF / IO error
     */
    private String readUserInput() {
        try {
            final String line = stdin.readLine();
            if (line == null) {
                log.warn("EOF reached while waiting for user input");
                return "<no input>";
            }
            final String trimmed = line.trim();
            log.debug("User provided input: {}", trimmed);
            return trimmed.isEmpty() ? "<no input>" : trimmed;
        } catch (Exception e) {
            log.error("Failed to read user input", e);
            return "<error reading input: " + e.getMessage() + ">";
        }
    }
}
