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

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

public abstract class MessageExpectation<R, T> implements Expectation<R, T> {
    @Override
    public boolean evaluate(R result, EvalExpectationContext<T> context) {
        for (AgentMessage message : context.getOldMessages()) {
            if (matches(message)) {
                return true;
            }
        }
        return false;
    }

    public abstract boolean matches(AgentMessage message);
}
