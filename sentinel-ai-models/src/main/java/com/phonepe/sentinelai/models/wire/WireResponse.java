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

import lombok.Value;

import java.util.List;

/**
 * Normalized single-turn response payload decoded from the provider response JSON; only one
 * turn's worth of data crosses this class.
 */
@Value
public class WireResponse {

    /**
     * Normalized finish reason constants shared across protocols.
     */
    public static final class FinishReasons {

        public static final String STOP = "stop";

        public static final String TOOL_CALLS = "tool_calls";

        public static final String LENGTH = "length";

        public static final String CONTENT_FILTER = "content_filter";

        public static final String REFUSED = "refused";

        private FinishReasons() {
            // Constant holder
        }
    }

    /**
     * One of {@link FinishReasons} values.
     */
    String finishReason;

    String content;

    String reasoningContent;

    String refusal;

    List<WireToolCall> toolCalls;

    WireUsage usage;

    /**
     * Provider-assigned id of this response, if any.
     */
    String responseId;
}
