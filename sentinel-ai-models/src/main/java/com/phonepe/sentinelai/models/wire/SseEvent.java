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

/**
 * One parsed Server-Sent-Events frame from a streaming model response.
 *
 * @param event Event name as sent in the {@code event:} line; null when the server sends only
 *              {@code data:} lines (OpenAI chat completions style).
 * @param data  Payload as sent in the {@code data:} line(s); multi-line data is joined with
 *              newlines per the SSE specification.
 */
public record SseEvent(
        String event,
        String data
) {

    /**
     * Sentinel data value some servers (OpenAI compatible) use to signal the end of a stream.
     */
    public static final String DONE_SENTINEL = "[DONE]";

    /**
     * @return True if this event carries the end-of-stream sentinel as its data.
     */
    public boolean isDoneSentinel() {
        return DONE_SENTINEL.equals(data);
    }
}
