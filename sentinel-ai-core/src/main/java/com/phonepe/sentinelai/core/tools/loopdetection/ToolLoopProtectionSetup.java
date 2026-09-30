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

import lombok.Builder;
import lombok.Value;

/**
 * Configuration for the tool loop protection of an agent run.
 *
 * <p>The protection works on model rounds. A round is one cycle of model call and the tool
 * calls the model requested in that cycle. Tool calls inside one round run in parallel, so
 * the detection ignores the execution order and compares rounds as sets.</p>
 *
 * <p>All values {@code <= 0} disable the corresponding layer. Set
 * {@link #enabled} to {@code false} to disable the complete protection.</p>
 */
@Value
@Builder
public class ToolLoopProtectionSetup {

    /**
     * Default number of rounds kept in the sliding window for repeat detection.
     */
    public static final int DEFAULT_WINDOW_SIZE = 20;

    /**
     * Default number of repeats of an identical round that triggers an instruction to the
     * model.
     */
    public static final int DEFAULT_INSTRUCTION_THRESHOLD = 3;

    /**
     * Default number of repeats of an identical round that terminates the run.
     */
    public static final int DEFAULT_TERMINATION_THRESHOLD = 5;

    /**
     * Default maximum number of model rounds with tool calls in a run.
     */
    public static final int DEFAULT_MAX_TOOL_ROUNDS = 25;

    /**
     * Default maximum number of tool calls in a run.
     */
    public static final int DEFAULT_MAX_TOOL_CALLS = 50;

    /**
     * Default setup with all layers enabled and default thresholds.
     */
    public static final ToolLoopProtectionSetup DEFAULT = ToolLoopProtectionSetup.builder().build();

    /**
     * Master switch for the complete protection.
     */
    @Builder.Default
    boolean enabled = true;

    /**
     * Number of rounds kept in the sliding window for repeat and cycle detection.
     */
    @Builder.Default
    int windowSize = DEFAULT_WINDOW_SIZE;

    /**
     * Number of repeats of an identical round that triggers an instruction to the model
     * to stop repeating itself. The instruction goes to the model as a user message.
     */
    @Builder.Default
    int instructionThreshold = DEFAULT_INSTRUCTION_THRESHOLD;

    /**
     * Number of repeats of an identical round that terminates the run with
     * {@link com.phonepe.sentinelai.core.errors.ErrorType#TOOL_LOOP_DETECTED}.
     */
    @Builder.Default
    int terminationThreshold = DEFAULT_TERMINATION_THRESHOLD;

    /**
     * Maximum number of model rounds with tool calls in a run. A value {@code <= 0}
     * disables this cap. The cap terminates the run with
     * {@link com.phonepe.sentinelai.core.errors.ErrorType#TOOL_CALL_BUDGET_EXCEEDED}.
     */
    @Builder.Default
    int maxToolRounds = DEFAULT_MAX_TOOL_ROUNDS;

    /**
     * Maximum number of tool calls in a run. A value {@code <= 0} disables this cap. The
     * cap terminates the run with
     * {@link com.phonepe.sentinelai.core.errors.ErrorType#TOOL_CALL_BUDGET_EXCEEDED}.
     */
    @Builder.Default
    int maxToolCalls = DEFAULT_MAX_TOOL_CALLS;
}
