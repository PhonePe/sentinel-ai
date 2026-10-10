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

package com.phonepe.sentinelai.evals.tests.expectations.executors;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.MessageExpectation;

/**
 * Base executor for expectations that inspect individual agent messages.
 *
 * @param <E> concrete message expectation type
 * @param <R> result/output type
 * @param <T> input/request type
 */
public abstract class MessageExpectationExecutor<E extends MessageExpectation<R, T>, R, T>
        implements
        ExpectationExecutor<R, T> {
    protected final E expectation;

    /**
     * Creates a message expectation executor.
     *
     * @param expectation expectation definition to evaluate
     */
    protected MessageExpectationExecutor(E expectation) {
        this.expectation = expectation;
    }

    /**
     * Evaluates the expectation by scanning prior agent messages for a matching entry.
     *
     * @param result  agent output (ignored by this base implementation)
     * @param context evaluation context containing prior messages
     * @return {@code true} when any message matches the expectation
     */
    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        for (var message : context.getOldMessages()) {
            if (matches(message)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the given message satisfies this expectation's matching criteria.
     *
     * @param message the agent message to test
     * @return {@code true} if this message matches
     */
    public abstract boolean matches(AgentMessage message);
}
