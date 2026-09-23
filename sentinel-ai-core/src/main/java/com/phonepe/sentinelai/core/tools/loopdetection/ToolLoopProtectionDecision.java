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

import lombok.Value;

/**
 * Decision of the tool loop protection for the run.
 */
@Value
public class ToolLoopProtectionDecision {

    /**
     * Types of decisions the protection can take.
     */
    public enum DecisionType {
        /**
         * Continue the run.
         */
        CONTINUE,
        /**
         * Send an instruction to the model and continue the run.
         */
        INSTRUCT,
        /**
         * Terminate the run because of a detected loop.
         */
        TERMINATE_LOOP,
        /**
         * Terminate the run because a budget was exceeded.
         */
        TERMINATE_BUDGET
    }

    DecisionType type;
    int roundCount;
    String detail;

    static ToolLoopProtectionDecision budgetExceeded(String budgetType, int actual, int max) {
        return new ToolLoopProtectionDecision(DecisionType.TERMINATE_BUDGET,
                                              actual,
                                              "Maximum %s exceeded: %d > %d".formatted(budgetType, actual, max));
    }

    static ToolLoopProtectionDecision continueRun() {
        return new ToolLoopProtectionDecision(DecisionType.CONTINUE, 0, null);
    }

    static ToolLoopProtectionDecision instruct(String instruction) {
        return new ToolLoopProtectionDecision(DecisionType.INSTRUCT, 0, instruction);
    }

    static ToolLoopProtectionDecision terminate(int roundCount) {
        return new ToolLoopProtectionDecision(DecisionType.TERMINATE_LOOP, roundCount, null);
    }

    public String instruction() {
        return detail;
    }
}
