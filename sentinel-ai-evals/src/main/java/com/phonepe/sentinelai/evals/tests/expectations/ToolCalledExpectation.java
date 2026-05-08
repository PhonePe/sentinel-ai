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

package com.phonepe.sentinelai.evals.tests.expectations;

import com.phonepe.sentinelai.evals.tests.MessageExpectation;

import lombok.With;

import java.util.Map;

/**
 * Expectation definition that asserts a named tool was called a given number of times,
 * optionally with specific parameters.
 *
 * Count-based evaluation logic is performed by {@code ToolCalledExpectationExecutor}.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public class ToolCalledExpectation<R, T> extends MessageExpectation<R, T> {

    private final String toolName;

    @With
    private int times;

    private final Map<String, Object> expectedParams;

    /**
     * Creates a tool-call expectation for a single invocation of the named tool.
     *
     * @param toolName tool identifier to match
     */
    public ToolCalledExpectation(String toolName) {
        this(toolName, 1, null);
    }

    /**
     * Creates a tool-call expectation with count and optional parameter matching.
     *
     * @param toolName       tool identifier to match
     * @param times          exact number of expected invocations
     * @param expectedParams optional arguments map that must match each invocation
     */
    public ToolCalledExpectation(String toolName, int times, Map<String, Object> expectedParams) {
        this.toolName = toolName;
        this.times = times;
        this.expectedParams = expectedParams;
    }

    /**
     * Returns the expected tool arguments.
     *
     * @return expected argument map, or {@code null} when arguments are not constrained
     */
    public Map<String, Object> getExpectedParams() {
        return expectedParams;
    }

    /**
     * Returns the exact number of invocations required.
     *
     * @return expected invocation count
     */
    public int getTimes() {
        return times;
    }

    /**
     * Returns the tool identifier to match.
     *
     * @return tool name or id
     */
    public String getToolName() {
        return toolName;
    }
}
