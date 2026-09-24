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

package com.phonepe.sentinelai.core.tools.loopdetection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.tools.loopdetection.ToolLoopProtectionDecision.DecisionType;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolLoopProtectionTest {

    private static final String SESSION_ID = "test-session";

    private static final String RUN_ID = "test-run";

    static Stream<Arguments> budgetScenarios() {
        final var callBudget = ToolLoopProtectionSetup.builder()
                .maxToolCalls(5)
                .instructionThreshold(100)
                .terminationThreshold(100)
                .build();
        final var roundBudget = ToolLoopProtectionSetup.builder()
                .maxToolRounds(3)
                .instructionThreshold(100)
                .terminationThreshold(100)
                .build();
        return Stream.of(
                         Arguments.of("tool call budget of 5 terminates after 3 rounds of 2 calls",
                                      callBudget,
                                      List.of(toolCall("call-a", "search", "{\"q\":1}"),
                                              toolCall("call-b", "fetch", "{\"q\":1}")),
                                      3,
                                      DecisionType.TERMINATE_BUDGET),
                         Arguments.of("tool round budget of 3 terminates after 4 rounds",
                                      roundBudget,
                                      List.of(toolCall("call", "search", "{\"q\":1}")),
                                      4,
                                      DecisionType.TERMINATE_BUDGET),
                         Arguments.of("no budget is configured so identical rounds terminate as a loop",
                                      ToolLoopProtectionSetup.builder()
                                              .maxToolCalls(0)
                                              .maxToolRounds(0)
                                              .build(),
                                      List.of(toolCall("call", "search", "{\"q\":1}")),
                                      10,
                                      DecisionType.TERMINATE_LOOP));
    }

    static Stream<Arguments> cycleScenarios() {
        final var cycleOnly = ToolLoopProtectionSetup.builder()
                .instructionThreshold(100) // Disable repeat-instruction so cycle detection shows
                .terminationThreshold(100) // Disable repeat-termination
                .build();
        final var disabled = ToolLoopProtectionSetup.builder()
                .enabled(false)
                .build();
        return Stream.of(
                         Arguments.of("ping-pong cycle of two tools instructs the model",
                                      cycleOnly,
                                      List.of("toolA", "toolB"),
                                      2,
                                      DecisionType.INSTRUCT),
                         Arguments.of("cycle of three tools instructs the model",
                                      cycleOnly,
                                      List.of("toolA", "toolB", "toolC"),
                                      2,
                                      DecisionType.INSTRUCT),
                         Arguments.of("identical rounds terminate after the cycle instruction was ignored",
                                      ToolLoopProtectionSetup.DEFAULT,
                                      List.of("search"),
                                      ToolLoopProtectionSetup.DEFAULT_TERMINATION_THRESHOLD + 2,
                                      DecisionType.TERMINATE_LOOP),
                         Arguments.of("disabled protection never acts on a ping-pong cycle",
                                      disabled,
                                      List.of("toolA", "toolB"),
                                      10,
                                      DecisionType.CONTINUE));
    }

    static Stream<Arguments> roundOrderVariations() {
        return Stream.of(
                         Arguments.of(
                                      "same round in different order counts as repeated",
                                      List.of(toolCall("call-1", "search", "{\"q\":\"a\"}"),
                                              toolCall("call-2", "fetch", "{\"q\":\"b\"}")),
                                      List.of(toolCall("call-3", "fetch", "{\"q\":\"b\"}"),
                                              toolCall("call-4", "search", "{\"q\":\"a\"}")),
                                      List.of(toolCall("call-5", "search", "{\"q\":\"a\"}"),
                                              toolCall("call-6", "fetch", "{\"q\":\"b\"}")),
                                      DecisionType.INSTRUCT),
                         Arguments.of(
                                      "different arguments in the round do not count as repeated",
                                      List.of(toolCall("call-1", "search", "{\"q\":\"a\"}"),
                                              toolCall("call-2", "fetch", "{\"q\":\"b\"}")),
                                      List.of(toolCall("call-3", "fetch", "{\"q\":\"c\"}"),
                                              toolCall("call-4", "search", "{\"q\":\"a\"}")),
                                      List.of(toolCall("call-5", "search", "{\"q\":\"a\"}"),
                                              toolCall("call-6", "fetch", "{\"q\":\"d\"}")),
                                      DecisionType.CONTINUE));
    }

    private static ToolLoopProtection protection(ToolLoopProtectionSetup setup) {
        return new ToolLoopProtection(setup, JsonUtils.createMapper(), Set.of());
    }

    private static ToolCall toolCall(String toolCallId, String toolName, String arguments) {
        return ToolCall.builder()
                .sessionId(SESSION_ID)
                .runId(RUN_ID)
                .toolCallId(toolCallId)
                .toolName(toolName)
                .arguments(arguments)
                .build();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("budgetScenarios")
    void budgetTermination(String name,
                           ToolLoopProtectionSetup setup,
                           List<ToolCall> round,
                           int rounds,
                           DecisionType expected) {
        final var protection = protection(setup);
        var decision = ToolLoopProtectionDecision.continueRun();
        for (var i = 0; i < rounds; i++) {
            decision = protection.recordRound(round);
        }
        assertEquals(expected, decision.getType());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cycleScenarios")
    void cycleDetection(String name,
                        ToolLoopProtectionSetup setup,
                        List<String> toolNames,
                        int cycles,
                        DecisionType expected) {
        final var protection = protection(setup);
        ToolLoopProtectionDecision decision = null;
        for (var cycle = 0; cycle < cycles; cycle++) {
            for (var t = 0; t < toolNames.size(); t++) {
                decision = protection.recordRound(List.of(toolCall("call-" + cycle + "-" + t,
                                                                   toolNames.get(t),
                                                                   "{}")));
            }
        }
        assertEquals(expected, decision.getType());
    }

    @Test
    void disabledProtectionNeverActs() {
        final var setup = ToolLoopProtectionSetup.builder().enabled(false).build();
        final var protection = protection(setup);
        for (var i = 0; i < 20; i++) {
            final var decision = protection.recordRound(List.of(toolCall("call-" + i, "search", "{\"q\":\"same\"}")));
            assertEquals(DecisionType.CONTINUE, decision.getType());
        }
    }

    @Test
    void emptyRoundsAreIgnored() {
        final var protection = protection(ToolLoopProtectionSetup.DEFAULT);
        for (var i = 0; i < 5; i++) {
            assertEquals(DecisionType.CONTINUE, protection.recordRound(List.of()).getType());
        }
    }

    @Test
    void exemptToolsAreIgnoredForRepeatDetection() {
        final var setup = ToolLoopProtectionSetup.builder()
                .instructionThreshold(2)
                .terminationThreshold(3)
                .maxToolRounds(0)
                .maxToolCalls(0)
                .build();
        final var protection = new ToolLoopProtection(setup, JsonUtils.createMapper(), Set.of("polling"));
        for (var i = 0; i < 5; i++) {
            final var decision = protection.recordRound(List.of(toolCall("call-" + i, "polling", "{}")));
            assertEquals(DecisionType.CONTINUE, decision.getType());
        }
    }

    @Test
    void identicalRoundsInstructThenTerminate() {
        final var protection = protection(ToolLoopProtectionSetup.DEFAULT);
        ToolLoopProtectionDecision decision = null;
        for (var i = 1; i <= ToolLoopProtectionSetup.DEFAULT_TERMINATION_THRESHOLD; i++) {
            decision = protection.recordRound(List.of(toolCall("call-" + i, "search", "{\"q\":\"same\"}")));
        }
        // The last recorded round hits the termination threshold.
        assertEquals(DecisionType.TERMINATE_LOOP, decision.getType());
        assertTrue(decision.getRoundCount() >= ToolLoopProtectionSetup.DEFAULT_TERMINATION_THRESHOLD);

        // An earlier repeat triggers the instruction first.
        final var protection2 = protection(ToolLoopProtectionSetup.DEFAULT);
        ToolLoopProtectionDecision second = null;
        for (var i = 1; i <= ToolLoopProtectionSetup.DEFAULT_INSTRUCTION_THRESHOLD; i++) {
            second = protection2.recordRound(List.of(toolCall("call-" + i, "search", "{\"q\":\"same\"}")));
        }
        assertEquals(DecisionType.INSTRUCT, second.getType());
        assertNotNull(second.instruction());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundOrderVariations")
    void parallelCallsInOneRoundAreTreatedAsOneUnit(String name,
                                                    List<ToolCall> first,
                                                    List<ToolCall> second,
                                                    List<ToolCall> third,
                                                    DecisionType expected) {
        final var protection = protection(ToolLoopProtectionSetup.DEFAULT);
        // Same round content, different call order inside the round. Order must not matter.
        assertEquals(DecisionType.CONTINUE, protection.recordRound(first).getType());
        assertEquals(DecisionType.CONTINUE, protection.recordRound(second).getType());
        assertEquals(expected, protection.recordRound(third).getType());
    }

    @Test
    void varyingRoundsDoNotTriggerProtection() {
        final var protection = protection(ToolLoopProtectionSetup.DEFAULT);
        for (var i = 0; i < 10; i++) {
            final var decision = protection.recordRound(List.of(toolCall("call-" + i, "search", "{\"q\":" + i + "}")));
            assertEquals(DecisionType.CONTINUE, decision.getType());
        }
    }
}
