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

/**
 * A {@link UnaryOperator} that can be used to add extra data to a {@link SessionSummary}.
 */
public abstract class SessionExtraDataOperator implements UnaryOperator<SessionSummary> {

    /**
     * A no-op implementation of {@link SessionExtraDataOperator} that returns an empty optional,
     * indicating that no extra data should be added to the session summary.
     */
    public static final class NoOp extends SessionExtraDataOperator {
        @Override
        protected Optional<Map<String, Object>> operate(SessionSummary sessionSummary) {
            return Optional.empty();
        }
    }

    /**
     * A fixed implementation of {@link SessionExtraDataOperator} that always returns the same extra data,
     * regardless of the input session summary.
     * This will make a copy of the input map once to ensure immutability and thread-safety.
     */
    public static final class Fixed extends SessionExtraDataOperator {
        private final Map<String, Object> extra;

        public Fixed(Map<String, Object> extra) {
            this.extra = Map.copyOf(extra);
        }

        @Override
        protected Optional<Map<String, Object>> operate(SessionSummary sessionSummary) {
            return Optional.of(extra);
        }
    }

    /**
     * Creates a new instance of {@link SessionExtraDataOperator} that does not add any extra data to the session
     * summary.
     *
     * @return a new instance of {@link SessionExtraDataOperator} that does not add any extra data to the session
     *         summary
     */
    public static SessionExtraDataOperator empty() {
        return new NoOp();
    }

    /**
     * Creates a new instance of {@link SessionExtraDataOperator} that always returns the same extra data,
     * regardless of the input session summary.
     *
     * @param extra the extra data to be added to the session summary
     * @return a new instance of {@link SessionExtraDataOperator} that always returns the same extra data
     */
    public static SessionExtraDataOperator fixed(Map<String, Object> extra) {
        return new Fixed(extra);
    }

    @Override
    public SessionSummary apply(SessionSummary sessionSummary) {
        final var extra = operate(sessionSummary).orElse(null);
        if (extra != null) {
            return sessionSummary.withExtra(extra);
        }
        return sessionSummary;
    }

    /**
     * Operates on the given session summary and returns an optional map of extra data to be added to the session
     * summary.
     *
     * Implementations of this method can choose to return an empty optional if no extra data should be added to
     * the session summary, or a non-empty optional if extra data should be added to the session summary.
     * Implementers need to be aware of the fact that this is called on every call of SessionSummary.saveSession()
     * 
     * @param sessionSummary the session summary to operate on
     * @return an optional map of extra data to be added to the session summary
     */
    protected abstract Optional<Map<String, Object>> operate(final SessionSummary sessionSummary);
}
