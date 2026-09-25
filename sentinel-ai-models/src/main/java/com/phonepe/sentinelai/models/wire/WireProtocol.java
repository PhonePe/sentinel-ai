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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.models.transport.SseEvent;

import java.util.List;
import java.util.Map;

/**
 * One implementation per wire format (OpenAI Chat Completions, OpenAI Responses, Anthropic
 * Messages, ...). Owns the message codec, request assembly and response/stream decoding. All wire
 * data is Jackson JSON; the model loop never sees a provider DTO.
 * <p>
 * Implementations must be stateless with respect to a single run; the model loop calls them from
 * possibly concurrent runs.
 */
public interface WireProtocol {

    /**
     * Deep merges {@code override} into {@code target}. Values in {@code override} win; nested
     * objects merge recursively.
     */
    static void deepMerge(final ObjectNode target, final JsonNode override) {
        override.fields().forEachRemaining(entry -> {
            final var key = entry.getKey();
            final var value = entry.getValue();
            if (value.isObject() && target.has(key) && target.get(key).isObject()) {
                deepMerge((ObjectNode) target.get(key), value);
            }
            else {
                target.set(key, value.deepCopy());
            }
        });
    }

    /**
     * Merges user extras into the request body last. Extras may fully override protocol-built
     * fields (deep merge, extras win). Unknown keys are allowed by design; this covers
     * open-weight servers (vLLM and similar) that add arbitrary request fields.
     *
     * @param body   Request body built by {@link #buildRequestBody}.
     * @param extras Free-form extras JSON; null means no extras.
     */
    default void applyExtras(ObjectNode body, JsonNode extras) {
        if (extras == null || extras.isNull() || !extras.isObject()) {
            return;
        }
        deepMerge(body, extras);
    }

    /**
     * Builds the full request body for one turn: settings, tools, output definitions and the
     * already translated messages. Implementations must not mutate the given message nodes; they
     * are shared across turns.
     *
     * @param ctx      Neutral call context.
     * @param messages Messages already translated by {@link #messageCodec()}.
     * @return Complete request body JSON.
     */
    ObjectNode buildRequestBody(WireContext ctx, List<JsonNode> messages);

    /**
     * Classifies an HTTP error into a neutral {@link ErrorType}.
     *
     * @param status    HTTP status code.
     * @param errorBody Error response body JSON; may be null/empty when the server sent none.
     * @return Neutral error type.
     */
    ErrorType classifyError(int status, JsonNode errorBody);

    /**
     * Decodes a single-turn blocking response body.
     *
     * @param body Response JSON.
     * @return Neutral response; never null (throw on undecodable responses).
     */
    WireResponse decodeResponse(JsonNode body);

    /**
     * Decodes one SSE frame into a neutral stream event. Return null for frames the protocol
     * ignores (comments, keep-alives, unrelated named events).
     *
     * @param event Parsed SSE frame.
     * @return Neutral event, or null when the frame carries nothing relevant.
     */
    WireStreamEvent decodeStreamEvent(SseEvent event);

    /**
     * @param ctx Neutral call context.
     * @return Full endpoint URL for this call.
     */
    String endpoint(WireContext ctx);

    /**
     * @param ctx Neutral call context.
     * @return Auth and protocol headers for this call (Bearer token, api key, version headers).
     */
    Map<String, String> headers(WireContext ctx);

    /**
     * @return The codec translating {@link com.phonepe.sentinelai.core.agentmessages.AgentMessage}
     *         to this wire format.
     */
    MessageCodec messageCodec();
}
