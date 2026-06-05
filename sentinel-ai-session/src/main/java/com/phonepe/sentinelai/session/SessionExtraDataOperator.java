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

package com.phonepe.sentinelai.session;

import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;

public abstract class SessionExtraDataOperator implements UnaryOperator<SessionSummary> {

    public static final class NoOp extends SessionExtraDataOperator {
        @Override
        protected Optional<Map<String, Object>> operate(SessionSummary sessionSummary) {
            return Optional.empty();
        }
    }

    public static SessionExtraDataOperator empty() {
        return new NoOp();
    }

    public static SessionExtraDataOperator fixed(Map<String, Object> extra) {
        return new SessionExtraDataOperator() {
            @Override
            protected Optional<Map<String, Object>> operate(SessionSummary sessionSummary) {
                return Optional.of(extra);
            }
        };
    }

    @Override
    public SessionSummary apply(SessionSummary sessionSummary) {
        final var extra = operate(sessionSummary).orElse(null);
        if (extra != null) {
            return sessionSummary.withExtra(extra);
        }
        return sessionSummary;
    }

    abstract protected Optional<Map<String, Object>> operate(final SessionSummary sessionSummary);
}
