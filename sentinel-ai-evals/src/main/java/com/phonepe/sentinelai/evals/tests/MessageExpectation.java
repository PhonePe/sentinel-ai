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

package com.phonepe.sentinelai.evals.tests;

/**
 * Base definition class for expectations that match against individual agent messages.
 *
 * Subclasses carry only the configuration data that describes WHAT to assert.
 * The matching predicate lives in a corresponding
 * {@link com.phonepe.sentinelai.evals.tests.expectations.executors.MessageExpectationExecutor}.
 *
 * @param <R> result/output type
 * @param <T> input/request type
 */
public abstract class MessageExpectation<R, T> extends Expectation<R, T> {

    protected MessageExpectation(String id) {
        super(id);
    }
}
