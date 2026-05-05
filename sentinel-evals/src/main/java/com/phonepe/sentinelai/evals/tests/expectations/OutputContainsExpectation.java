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

import com.google.common.base.Strings;

import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.Expectation;

import lombok.ToString;

import java.util.Locale;

@ToString
public class OutputContainsExpectation<T> implements Expectation<String, T> {
    private final String containsSubstring;

    public OutputContainsExpectation(String containsSubstring) {
        this.containsSubstring = containsSubstring;
    }

    @Override
    public boolean evaluate(String result, EvalExpectationContext<T> context) {
        if (result == null) {
            return Strings.isNullOrEmpty(containsSubstring);
        }
        return result.toLowerCase(Locale.ROOT).contains(containsSubstring.toLowerCase(Locale.ROOT));
    }

}
