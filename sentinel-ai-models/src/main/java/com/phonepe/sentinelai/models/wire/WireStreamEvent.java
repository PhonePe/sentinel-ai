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

/**
 * Streaming events decoded from provider SSE frames; consumed in order by the model loop.
 */
public sealed interface WireStreamEvent permits WireStreamEvent.ContentDelta,
        WireStreamEvent.ReasoningDelta,
        WireStreamEvent.ToolCallDelta,
        WireStreamEvent.ToolCallComplete,
        WireStreamEvent.StreamFinishEvent,
        WireStreamEvent.StreamUsageEvent {

    /**
     * A fragment of the response text content.
     */
    @Value
    class ContentDelta implements WireStreamEvent {

        String content;
    }

    /**
     * A fragment of the reasoning/thinking content.
     */
    @Value
    class ReasoningDelta implements WireStreamEvent {

        String content;
    }

    /**
     * A fragment of one tool call. The index orders fragments of the same call; the id, name
     * and arguments fields are cumulative fragments to be merged per index.
     */
    @Value
    class ToolCallDelta implements WireStreamEvent {

        int index;

        String id;

        String name;

        String argumentsFragment;
    }

    /**
     * The complete form of one tool call, sent by providers that emit the finished item after
     * the fragments. The loop replaces any accumulator state for this index with these values.
     */
    @Value
    class ToolCallComplete implements WireStreamEvent {

        int index;

        String id;

        String name;

        String arguments;
    }

    /**
     * The provider signaled the stop reason for the turn; delivered at most once per turn.
     */
    @Value
    class StreamFinishEvent implements WireStreamEvent {

        /**
         * One of {@link WireResponse.FinishReasons} values.
         */
        String finishReason;

        String refusal;

        WireUsage usage;

        /**
         * Provider-assigned id of the finished response.
         */
        String responseId;

        public StreamFinishEvent(final String finishReason, final String refusal) {
            this(finishReason, refusal, null);
        }

        public StreamFinishEvent(final String finishReason, final String refusal, final WireUsage usage) {
            this(finishReason, refusal, usage, null);
        }

        public StreamFinishEvent(final String finishReason,
                                 final String refusal,
                                 final WireUsage usage,
                                 final String responseId) {
            this.finishReason = finishReason;
            this.refusal = refusal;
            this.usage = usage;
            this.responseId = responseId;
        }
    }

    /**
     * Usage reported by the provider, typically on the final frames.
     */
    @Value
    class StreamUsageEvent implements WireStreamEvent {

        WireUsage usage;
    }

    static boolean isIgnorable(final WireStreamEvent event) {
        return event == null;
    }
}
