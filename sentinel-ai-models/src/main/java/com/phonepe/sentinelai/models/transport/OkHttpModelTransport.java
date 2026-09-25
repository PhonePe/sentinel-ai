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

package com.phonepe.sentinelai.models.transport;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * OkHttp based {@link ModelTransport}. Wraps a shared {@link OkHttpClient} owned by the caller.
 * Interceptors added to that client (retries, auth, metrics, circuit breaking) apply to every
 * model call made through this transport without any protocol changes.
 * <p>
 * Note: this transport never closes the caller's client.
 */
@Slf4j
public class OkHttpModelTransport implements ModelTransport {

    private static final MediaType JSON = MediaType.parse("application/json");
    private static final String SSE_MEDIA_TYPE = "text/event-stream";

    /**
     * Exception carrying the HTTP error details of a failed stream request so the model layer
     * can classify the error the same way it classifies blocking call failures.
     */
    public static class HttpStreamException extends IOException {

        private final int status;
        private final Map<String, String> headers;
        private final byte[] body;

        public HttpStreamException(final int status, final Map<String, String> headers, final byte[] body) {
            super("HTTP %d received while opening model stream".formatted(status));
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        public byte[] body() {
            return body;
        }

        public Map<String, String> headers() {
            return headers;
        }

        public int status() {
            return status;
        }
    }

    private static final class SseEventIterator implements java.util.Iterator<SseEvent>, AutoCloseable {

        private final Response response;
        private final BufferedReader reader;
        private SseEvent next;

        SseEventIterator(final Response response, final ResponseBody body) {
            this.response = response;
            this.reader = new BufferedReader(new InputStreamReader(body.byteStream(), StandardCharsets.UTF_8));
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

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            try {
                next = readEvent();
                return next != null;
            }
            catch (final IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }

        @Override
        public SseEvent next() {
            final var event = hasNext() ? next : null;
            next = null;
            if (event == null) {
                throw new java.util.NoSuchElementException();
            }
            return event;
        }

        /**
         * Reads one SSE frame. Supports {@code event:} and {@code data:} lines, multi-line data
         * (joined with newlines) and comment lines. Blank lines terminate a frame.
         */
        private SseEvent readEvent() throws IOException {
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

    private static final class SseSpliterator implements java.util.Spliterator<SseEvent> {

        private static final int ORDERED = java.util.Spliterator.ORDERED;

        private static final int NONNULL = java.util.Spliterator.NONNULL;

        private final SseEventIterator iterator;

        SseSpliterator(final SseEventIterator iterator) {
            this.iterator = iterator;
        }

        @Override
        public int characteristics() {
            return ORDERED | NONNULL;
        }

        @Override
        public long estimateSize() {
            return Long.MAX_VALUE;
        }

        @Override
        public boolean tryAdvance(final Consumer<? super SseEvent> action) {
            if (iterator.hasNext()) {
                action.accept(iterator.next());
                return true;
            }
            return false;
        }

        @Override
        public java.util.Spliterator<SseEvent> trySplit() {
            return null;
        }
    }

    private final OkHttpClient client;

    private final Executor executor;

    public OkHttpModelTransport(final OkHttpClient client) {
        this(client, Runnable::run);
    }

    public OkHttpModelTransport(@NonNull final OkHttpClient client, @NonNull final Executor executor) {
        this.client = client;
        this.executor = executor;
    }

    public static OkHttpModelTransport of(final OkHttpClient client) {
        return new OkHttpModelTransport(client);
    }

    public static OkHttpModelTransport of(final OkHttpClient client, final Executor executor) {
        return new OkHttpModelTransport(client, executor);
    }

    private static Map<String, String> headers(final Response response) {
        final var map = new HashMap<String, String>();
        response.headers().forEach(pair -> map.put(pair.getFirst(), pair.getSecond()));
        return map;
    }

    @Override
    public CompletableFuture<TransportResponse> execute(final TransportRequest request) {
        final var okRequest = buildRequest(request);
        final var future = new CompletableFuture<TransportResponse>();
        client.newCall(okRequest).enqueue(new Callback() {
            @Override
            public void onFailure(final Call call, final IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(final Call call, final Response response) {
                try (response; final var body = response.body()) {
                    if (body == null) {
                        future.complete(new TransportResponse(response.code(),
                                                              headers(response),
                                                              new byte[0]));
                        return;
                    }
                    future.complete(new TransportResponse(response.code(),
                                                          headers(response),
                                                          body.bytes()));
                }
                catch (final IOException e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    @Override
    public CompletableFuture<Stream<SseEvent>> stream(final TransportRequest request) {
        final var okRequest = buildRequest(request);
        final var future = new CompletableFuture<Stream<SseEvent>>();
        client.newCall(okRequest).enqueue(new Callback() {
            @Override
            public void onFailure(final Call call, final IOException e) {
                future.completeExceptionally(e);
            }

            @Override
            public void onResponse(final Call call, final Response response) {
                if (!response.isSuccessful()) {
                    // Read the body so the caller can classify the error, then fail the stream.
                    try (response) {
                        final var body = response.body() == null ? new byte[0]
                                : response.body().bytes();
                        future.completeExceptionally(new HttpStreamException(response.code(),
                                                                             headers(response),
                                                                             body));
                    }
                    catch (final IOException e) {
                        future.completeExceptionally(e);
                    }
                    return;
                }
                // Stream the SSE body. The reader iterator drives the OkHttp source on the
                // consuming thread; events surface lazily as they arrive.
                final var body = response.body();
                if (body == null) {
                    future.completeExceptionally(new IOException("No response body for stream"));
                    return;
                }
                try {
                    final var iterator = new SseEventIterator(response, body);
                    final var stream = StreamSupport
                            .stream(new SseSpliterator(iterator), false)
                            .onClose(() -> {
                                try {
                                    iterator.close();
                                }
                                catch (final IOException e) {
                                    log.debug("Error closing SSE stream", e);
                                }
                            });
                    future.complete(stream);
                }
                catch (final Exception e) {
                    future.completeExceptionally(e);
                }
            }
        });
        return future;
    }

    private Request buildRequest(final TransportRequest request) {
        final var builder = new Request.Builder()
                .url(request.url())
                .method(request.method(), RequestBody.create(request.body(), JSON));
        request.headers().forEach(builder::header);
        return builder.build();
    }

    private void run(final Consumer<Void> action) {
        executor.execute(() -> action.accept(null));
    }
}
