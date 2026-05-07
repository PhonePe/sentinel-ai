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

package com.phonepe.sentinelai.evals.tests.expectations.jsonpath;

/**
 * Comparison operators supported by JSONPath-based expectations.
 */
public enum Operator {
    /** Equality comparison. */
    EQ,
    /** Inequality comparison. */
    NE,
    /** Greater-than comparison. */
    GT,
    /** Greater-than-or-equal comparison. */
    GTE,
    /** Less-than comparison. */
    LT,
    /** Less-than-or-equal comparison. */
    LTE,
    /** Membership comparison against a collection of allowed values. */
    IN,
    /** Non-membership comparison against a collection of disallowed values. */
    NOT_IN
}
