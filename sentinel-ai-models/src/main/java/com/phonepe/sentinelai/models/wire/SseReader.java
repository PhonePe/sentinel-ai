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

import lombok.NonNull;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Reads a Server-Sent-Events body line by line and yields {@link SseEvent} frames.
 * Implements parsing for: {@code event:} and {@code data:}
 * lines, multi-line data joined with newlines, comment lines ignored and unknown SSE fields
 * ignored per the specification. The iterator drives the HTTP body on the consuming thread;
 * events surface lazily as they arrive.
 */
public final class SseReader implements AutoCloseable {

    /**
     * Ordered, non-null spliterator over the events of this reader.
     */
    private final class Spliter implements Spliterator<SseEvent> {

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.NONNULL;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean tryAdvance(final Consumer<? super SseEvent> action) {
            if (next == null) {
                try {
                    next = read();
                }
                catch (final IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
                if (next == null) {
                    return false;
                }
            }
            action.accept(next);
            next = null;
            return true;
        }

        @Override
        public Spliterator<SseEvent> trySplit() {
            return null;
        }
    }

    private final Response response;

    private final BufferedReader reader;

    private SseEvent next;

    public SseReader(@NonNull final Response response, @NonNull final ResponseBody body) {
        this.response = response;
        this.reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8));
    }

    /**
     * Streams the events of the body. Closing the stream closes the reader and the response.
     *
     * @param response OkHttp response that owns the stream.
     * @param body     Response body of the stream.
     * @return Lazy stream of parsed SSE events.
     */
    public static Stream<SseEvent> stream(@NonNull final Response response, @NonNull final ResponseBody body) {
        final var reader = new SseReader(response, body);
        return StreamSupport.stream(reader.new Spliter(), false).onClose(() -> {
            try {
                reader.close();
            }
            catch (final IOException e) {
                // Ignore close failures; the stream is done either way.
            }
        });
    }

    private static boolean isFieldLine(final String line) {
        final var colon = line.indexOf(':');
        if (colon <= 0) {
            return false;
        }
        return line.substring(0, colon)
                .chars()
                .allMatch(c -> Character.isLetterOrDigit(c) || c == '-');
    }

    private static String valueOf(final String line) {
        final var value = line.substring(line.indexOf(':') + 1);
        return value.startsWith(" ") ? value.substring(1) : value;
    }

    @Override
    public void close() throws IOException {
        try {
            reader.close();
        }
        finally {
            response.close();
        }
    }

    /**
     * Reads the next event, or null at the end of the stream.
     *
     * @return The next parsed event, or null at the end of the stream.
     * @throws IOException When the body read fails.
     */
    public SseEvent read() throws IOException {
        String eventName = null;
        final var dataLines = new ArrayList<String>();
        String line;
        var sawAnyLine = false;
        while ((line = reader.readLine()) != null) {
            sawAnyLine = true;
            if (line.isEmpty()) {
                if (eventName == null && dataLines.isEmpty()) {
                    // Blank line before frame content; skip.
                    continue;
                }
                break;
            }
            if (line.startsWith(":")) {
                // SSE comment; ignore.
                continue;
            }
            if (line.startsWith("event:")) {
                eventName = valueOf(line);
            }
            else if (line.startsWith("data:")) {
                dataLines.add(valueOf(line));
            }
            else if (isFieldLine(line)) {
                // Unknown SSE fields (id:, retry:, ...) are ignored per the spec.
            }
            else {
                // A bare payload line without a "data:" prefix counts as data.
                dataLines.add(line);
            }
        }
        if (!sawAnyLine || (eventName == null && dataLines.isEmpty())) {
            return null; // End of stream
        }
        final var data = String.join("\n", dataLines);
        if (data.isEmpty() && eventName == null) {
            return null;
        }
        return new SseEvent(eventName, data);
    }
}
