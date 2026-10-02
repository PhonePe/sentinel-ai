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

package com.phonepe.sentinelai.models.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Strings;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import okhttp3.Request;

/**
 * A {@link RequestTransformer} that sends the current session id to a provider as a
 * cache-affinity signal. Providers use different mechanisms, so both the header name and the
 * body location are configurable; set only the mechanism the provider supports.
 *
 * <p>Known provider mechanisms:
 * <ul>
 * <li>OpenRouter: header {@code x-session-id} or body pointer {@code /session_id}</li>
 * <li>OpenAI API: body pointer {@code /prompt_cache_key} (no header)</li>
 * <li>Fireworks AI: header {@code x-session-affinity}</li>
 * <li>Anthropic-compatible gateways: header {@code X-Session-Id}</li>
 * </ul>
 *
 * <p>When both settings are null or empty, the transformer does nothing. When the run has no
 * session id, the transformer adds nothing.
 * <pre>
 * SessionIdInjectionTransformer.builder()
 * .header("x-session-id")
 * .bodyPath("/session_id")
 * .build()
 * </pre>
 */
@Value
@Builder
@Jacksonized
public class SessionIdInjectionTransformer implements RequestTransformer {

    /**
     * HTTP header name that carries the session id; optional.
     */
    String header;

    /**
     * JSON pointer (RFC 6901) to the body location that carries the session id; optional.
     * Intermediate object nodes are created when absent.
     */
    String bodyPath;

    /**
     * Writes the value at the pointer location, creating missing intermediate object nodes.
     */
    private static void putAtPointer(final ObjectNode body, final String path, final String value) {
        if (path.isEmpty() || !path.startsWith("/")) {
            throw new IllegalArgumentException(
                                               "Session id body pointer must start with '/': " + path);
        }
        final var segments = path.substring(1).split("/");
        var current = body;
        for (var i = 0; i < segments.length - 1; i++) {
            final var segment = segments[i];
            final JsonNode child = current.get(segment);
            if (child == null || child.isNull()) {
                current = current.putObject(segment);
            }
            else if (child.isObject()) {
                current = (ObjectNode) child;
            }
            else {
                throw new IllegalArgumentException(
                                                   "Session id body pointer meets non-object node at: " + segment);
            }
        }
        current.put(segments[segments.length - 1], value);
    }

    @Override
    public void transform(final Request.Builder requestBuilder,
                          final ObjectNode body,
                          final RequestTransformerContext ctx) {
        final var sessionId = ctx.sessionId().orElse(null);
        if (Strings.isNullOrEmpty(sessionId)) {
            return;
        }
        if (!Strings.isNullOrEmpty(header)) {
            requestBuilder.header(header, sessionId);
        }
        if (!Strings.isNullOrEmpty(bodyPath)) {
            putAtPointer(body, bodyPath, sessionId);
        }
    }
}
