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

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Transport abstraction over the HTTP client used to talk to model providers.
 * <p>
 * Implementations send raw request bytes and return raw response bytes. All wire format
 * knowledge stays in the {@code WireProtocol} layer. The default implementation is
 * {@link OkHttpModelTransport} which wraps a shared {@code OkHttpClient}; future cross-cutting
 * features (retries, auth injection, metrics, circuit breaking) are OkHttp interceptors on that
 * shared client and need no changes here.
 */
public interface ModelTransport {

    /**
     * Releases any resources held by this transport. Transports wrapping a user-provided client
     * do not close that client.
     */
    default void close() {
        // no-op by default
    }

    /**
     * Executes a blocking request/response call.
     *
     * @param request Request to send.
     * @return Future completing with the response (any HTTP status; error classification is up
     *         to the caller), or failing with an exception for transport level failures
     *         (network errors, timeouts).
     */
    CompletableFuture<TransportResponse> execute(TransportRequest request);

    /**
     * Executes a Server-Sent-Events request and returns the parsed event stream.
     *
     * @param request Request to send.
     * @return Future completing with a stream of parsed SSE events, or failing with an exception
     *         for transport level failures.
     */
    CompletableFuture<Stream<SseEvent>> stream(TransportRequest request);
}
