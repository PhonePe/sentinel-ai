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

package com.phonepe.sentinelai.models.wire;

import java.util.List;

/**
 * Normalized single-turn response payload decoded from the provider response JSON. This is not
 * history-sized; the model loop rebuilds history from neutral messages, so only one turn's worth
 * of data crosses this record.
 *
 * @param finishReason     Normalized finish reason; one of {@link FinishReasons} values.
 * @param content          Text content of the response; null when absent.
 * @param reasoningContent Reasoning/thinking content of the response; null when absent.
 * @param refusal          Refusal text of the response; null when absent.
 * @param toolCalls        Tool calls requested by the model; empty when none.
 * @param usage            Token usage reported by the provider; null when absent.
 */
public record WireResponse(
        String finishReason,
        String content,
        String reasoningContent,
        String refusal,
        List<WireToolCall> toolCalls,
        WireUsage usage
) {

    /**
     * Normalized finish reason constants shared across protocols. Each protocol maps its
     * provider-specific stop signal into one of these.
     */
    public static final class FinishReasons {

        /**
         * The model stopped generating naturally.
         */
        public static final String STOP = "stop";

        /**
         * The model stopped to call one or more tools.
         */
        public static final String TOOL_CALLS = "tool_calls";

        /**
         * The model hit the token/length limit.
         */
        public static final String LENGTH = "length";

        /**
         * The model output was filtered by provider content filtering.
         */
        public static final String CONTENT_FILTER = "content_filter";

        /**
         * The model refused the request.
         */
        public static final String REFUSED = "refused";

        private FinishReasons() {
            // Constant holder
        }
    }
}
