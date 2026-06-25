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

import com.phonepe.sentinelai.evals.tests.Expectation;


/**
 * Expectation definition that asserts the output string contains a given substring
 * (case-insensitive).
 *
 * Computation is performed by {@code OutputContainsExpectationExecutor}.
 *
 * @param <T> input/request type
 */
public class OutputContainsExpectation<T> extends Expectation<String, T> {

    private final String containsSubstring;

    /**
     * Creates a substring containment expectation.
     *
     * @param id                unique identifier for this expectation
     * @param containsSubstring substring expected to appear in the output
     */
    public OutputContainsExpectation(String id, String containsSubstring) {
        super(id);
        this.containsSubstring = containsSubstring;
    }

    /**
     * Returns the expected substring.
     *
     * @return substring expected in the output
     */
    public String getContainsSubstring() {
        return containsSubstring;
    }
}
