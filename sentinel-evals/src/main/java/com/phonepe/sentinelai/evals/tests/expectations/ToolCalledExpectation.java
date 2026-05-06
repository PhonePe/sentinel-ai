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

    public ToolCalledExpectation(String toolName) {
        this(toolName, 1, null);
    }

    public ToolCalledExpectation(String toolName, int times, Map<String, Object> expectedParams) {
        this.toolName = toolName;
        this.times = times;
        this.expectedParams = expectedParams;
    }

    public Map<String, Object> getExpectedParams() {
        return expectedParams;
    }

    public int getTimes() {
        return times;
    }

    public String getToolName() {
        return toolName;
    }
}
