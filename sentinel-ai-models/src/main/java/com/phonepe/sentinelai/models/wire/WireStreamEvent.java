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

public sealed interface WireStreamEvent permits WireStreamEvent.ContentDelta,
        WireStreamEvent.ReasoningDelta,
        WireStreamEvent.ToolCallDelta,
        WireStreamEvent.StreamFinishEvent,
        WireStreamEvent.StreamUsageEvent {

    /**
     * A fragment of the response text content.
     */
    record ContentDelta(String content) implements WireStreamEvent {
    }

    /**
     * A fragment of the reasoning/thinking content.
     */
    record ReasoningDelta(String content) implements WireStreamEvent {
    }

    /**
     * A fragment of one tool call. Providers stream tool calls as partial objects; the index
     * orders fragments of the same call, and the id/name/arguments fields are cumulative
     * fragments to be merged (concatenated) per index.
     */
    record ToolCallDelta(
            int index,
            String id,
            String name,
            String argumentsFragment
    )
            implements
            WireStreamEvent {
    }

    /**
     * The provider signaled the stop reason for the turn. Delivered at most once per turn; the
     * loop deduplicates repeated trailing stop signals. Providers that carry the usage on the
     * same final frame pass it here; the loop merges it into the run stats.
     */
    record StreamFinishEvent(
            String finishReason,
            String refusal,
            WireUsage usage
    ) implements WireStreamEvent {

        public StreamFinishEvent(final String finishReason, final String refusal) {
            this(finishReason, refusal, null);
        }
    }

    /**
     * Usage reported by the provider, typically on the final frames.
     */
    record StreamUsageEvent(WireUsage usage) implements WireStreamEvent {
    }

    /**
     * Helper for implementors of {@link WireProtocol#decodeStreamEvent(SseEvent)}: a
     * provider-level neutral marker for frames the protocol layer ignores.
     */
    static boolean isIgnorable(final WireStreamEvent event) {
        return event == null;
    }
}
