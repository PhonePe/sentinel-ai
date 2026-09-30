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

import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Protects an agent run against model runs that repeat the same tool calls in a loop
 * without making progress.
 *
 * <p>The protection works on model rounds. A round is one model call and the tool calls
 * the model requested in it. Tool calls inside one round run in parallel, so the round is
 * compared as a set: the order of the calls inside the round does not matter.</p>
 *
 * <p>Layers:</p>
 * <ol>
 * <li><b>Round repeat detection:</b> if the same round (same set of canonical tool
 * calls) repeats often enough inside the sliding window, the protection first sends an
 * instruction to the model, then terminates the run if the repeat continues.</li>
 * <li><b>Cycle detection:</b> if the sequence of rounds ends with a cycle of length
 * 2 to 5 that repeats at least twice, for example A, B, A, B, the protection first
 * sends an instruction to the model, then terminates the run if the cycle continues.
 * Rounds of length 1 are covered by layer 1.</li>
 * <li><b>Budgets:</b> the run is terminated when it exceeds the maximum number of
 * tool rounds or the maximum number of tool calls. This backstop also catches loops
 * with always-changing arguments.</li>
 * </ol>
 *
 * <p>Instances are not thread safe by design. The model layer must call
 * {@link #recordRound(List)} once per model round from a single thread, after all tool
 * calls of the round are known.</p>
 */
@Slf4j
public class ToolLoopProtection {

    private static final int MIN_CYCLE_LENGTH = 2;

    private static final int MAX_CYCLE_LENGTH = 5;
    private static final int CYCLE_REPEATS = 2;

    /**
     * Maximum number of instructions sent to the model in one run.
     */
    private static final int MAX_INSTRUCTIONS = 2;
    /**
     * Instruction text sent to the model when a loop is detected the first time.
     */
    private static final String INSTRUCTION_TEMPLATE = ("The last %d model rounds repeated the same tool calls "
            + "with the same arguments. The conversation already contains the responses to these tool calls. "
            + "Do not call the same tools with the same arguments again. "
            + "Read the previous tool responses and produce the final answer now.");


    private final ToolLoopProtectionSetup setup;

    private final ObjectMapper mapper;

    private final Set<String> exemptTools;

    /**
     * Sliding window of round keys. Each entry is the canonical key of one recorded round.
     */
    private final Deque<String> roundKeys;

    private int toolRounds;

    private int toolCalls;

    private boolean instructionSent;

    private int instructionsSent;

    /**
     * @param setup       Configuration of the protection layers. Must not be null.
     * @param mapper      Object mapper used to canonicalize tool call arguments.
     * @param exemptTools Names of tools that are exempt from repeat and cycle detection.
     */
    public ToolLoopProtection(ToolLoopProtectionSetup setup, ObjectMapper mapper, Set<String> exemptTools) {
        this.setup = setup;
        this.mapper = mapper;
        this.exemptTools = null == exemptTools ? Set.of() : Set.copyOf(exemptTools);
        this.roundKeys = new ArrayDeque<>();
        this.instructionSent = false;
        this.instructionsSent = 0;
    }

    /**
     * Records one completed model round and evaluates all protection layers.
     *
     * @param roundToolCalls The tool calls the model requested in this round. The order
     *                       inside the round does not matter. Must not be null.
     * @return The decision for the run. Never null.
     */
    public ToolLoopProtectionDecision recordRound(List<ToolCall> roundToolCalls) {
        if (!setup.isEnabled() || roundToolCalls.isEmpty()) {
            return ToolLoopProtectionDecision.continueRun();
        }
        toolRounds++;
        toolCalls += roundToolCalls.size();

        final var budgetDecision = evaluateBudgets();
        if (null != budgetDecision) {
            return budgetDecision;
        }

        final var trackedCalls = roundToolCalls.stream()
                .filter(call -> !exemptTools.contains(call.getToolName()))
                .toList();
        if (trackedCalls.isEmpty()) {
            return ToolLoopProtectionDecision.continueRun();
        }

        final var roundKey = roundKey(trackedCalls);
        roundKeys.addLast(roundKey);
        while (roundKeys.size() > Math.max(1, setup.getWindowSize())) {
            roundKeys.removeFirst();
        }

        final var repeatCount = countKey(roundKey);
        final var terminationThreshold = setup.getTerminationThreshold();
        final var instructionThreshold = setup.getInstructionThreshold();
        if (terminationThreshold > 0 && repeatCount >= terminationThreshold) {
            log.warn("Tool loop protection: round repeated {} times, terminating run", repeatCount);
            return ToolLoopProtectionDecision.terminate(repeatCount);
        }
        final var cycle = cycleLength();
        if (cycle > 0) {
            if (instructionSent) {
                log.warn("Tool loop protection: cycle of length {} continues, terminating run", cycle);
                return ToolLoopProtectionDecision.terminate(cycle * CYCLE_REPEATS);
            }
            if (instructionThreshold > 0 && instructionsSent < MAX_INSTRUCTIONS) {
                instructionSent = true;
                instructionsSent++;
                log.warn("Tool loop protection: cycle of length {} detected, instructing model", cycle);
                return ToolLoopProtectionDecision.instruct(INSTRUCTION_TEMPLATE.formatted(cycle * CYCLE_REPEATS));
            }
        }
        if (instructionThreshold > 0 && repeatCount >= instructionThreshold && instructionsSent < MAX_INSTRUCTIONS) {
            instructionSent = true;
            instructionsSent++;
            log.warn("Tool loop protection: round repeated {} times, instructing model", repeatCount);
            return ToolLoopProtectionDecision.instruct(INSTRUCTION_TEMPLATE.formatted(repeatCount));
        }
        return ToolLoopProtectionDecision.continueRun();
    }

    private int countKey(String key) {
        var count = 0;
        for (final var existing : roundKeys) {
            if (existing.equals(key)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Checks whether the recorded round keys end with a cycle of length 2 to 5 that
     * repeats at least twice.
     *
     * @return The length of the detected cycle, or 0 when no cycle is detected.
     */
    private int cycleLength() {
        final var keys = new ArrayList<>(roundKeys);
        final var maxLength = Math.min(MAX_CYCLE_LENGTH, keys.size() / (CYCLE_REPEATS - 1));
        for (var length = MIN_CYCLE_LENGTH; length <= maxLength; length++) {
            // Check that the block of the last `length` rounds repeats CYCLE_REPEATS times.
            var matched = true;
            for (var repeat = 1; repeat < CYCLE_REPEATS && matched; repeat++) {
                for (var i = 0; i < length && matched; i++) {
                    final var lastIdx = keys.size() - 1 - i;
                    final var prevIdx = lastIdx - length * repeat;
                    if (prevIdx < 0 || !keys.get(lastIdx).equals(keys.get(prevIdx))) {
                        matched = false;
                    }
                }
            }
            if (matched) {
                return length;
            }
        }
        return 0;
    }

    private ToolLoopProtectionDecision evaluateBudgets() {
        final var maxRounds = setup.getMaxToolRounds();
        if (maxRounds > 0 && toolRounds > maxRounds) {
            log.warn("Tool loop protection: tool round budget exceeded: {} > {}", toolRounds, maxRounds);
            return ToolLoopProtectionDecision.budgetExceeded("tool rounds", toolRounds, maxRounds);
        }
        final var maxCalls = setup.getMaxToolCalls();
        if (maxCalls > 0 && toolCalls > maxCalls) {
            log.warn("Tool loop protection: tool call budget exceeded: {} > {}", toolCalls, maxCalls);
            return ToolLoopProtectionDecision.budgetExceeded("tool calls", toolCalls, maxCalls);
        }
        return null;
    }

    private String roundKey(List<ToolCall> calls) {
        final var keys = new ArrayList<String>(calls.size());
        for (final var call : calls) {
            keys.add(new ToolCallKey(call.getToolName(), call.getArguments(), mapper).asString());
        }
        // A round is a set: order of calls inside one round does not matter.
        keys.sort(String::compareTo);
        return String.join("\u0001", keys);
    }

}
