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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.tools.ParameterMapper;

import java.util.Comparator;
import java.util.List;

/**
 * One implementation per wire format (OpenAI Chat Completions, OpenAI Responses, Anthropic
 * Messages, ...). Owns the message codec, request assembly and response/stream decoding. All wire
 * data is Jackson JSON; the model loop never sees a provider DTO.
 * <p>
 * Implementations must be stateless: they take the run {@link com.fasterxml.jackson.databind.ObjectMapper}
 * through the {@link WireContext} and hold no per-run state, so one instance serves every run and
 * every mapper. The model loop calls them from possibly concurrent runs.
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
     * Applies the model settings to the request body. Shared helper for OpenAI style protocols
     * that share the field names; Chat Completions and Responses keep their own variants for the
     * fields that differ.
     *
     * @param ctx      Neutral call context; carries the run mapper.
     * @param settings Model settings; may be null.
     * @param body     Request body under construction.
     */
    default void applyModelSettings(final WireContext ctx, final ModelSettings settings, final ObjectNode body) {
        if (settings == null) {
            return;
        }
        if (settings.getTemperature() != null) {
            body.put("temperature", settings.getTemperature().doubleValue());
        }
        if (settings.getTopP() != null) {
            body.put("top_p", settings.getTopP().doubleValue());
        }
    }

    /**
     * Builds the flat tool list of the wire format. Shared helper for protocols whose tools are a
     * flat array (Responses); nested shapes build their own.
     *
     * @param ctx Neutral call context.
     * @return Tool array node; empty when the context has no tools.
     */
    default ArrayNode buildFlatTools(WireContext ctx) {
        final var mapper = ctx.getMapper();
        final var parameterMapper = new ParameterMapper(mapper);
        final var toolArray = mapper.createArrayNode();
        ctx.getTools()
                .values()
                .stream()
                .sorted(Comparator.comparing(tool -> tool.getToolDefinition().getId()))
                .forEach(tool -> {
                    final var definition = tool.getToolDefinition();
                    final var toolNode = mapper.createObjectNode();
                    toolNode.put("type", "function");
                    toolNode.put("name", definition.getId());
                    toolNode.put("description", definition.getDescription());
                    toolNode.set("parameters", tool.accept(parameterMapper));
                    toolNode.put("strict", definition.isStrictSchema());
                    toolArray.add(toolNode);
                });
        return toolArray;
    }

    /**
     * Builds the full request body for one turn: settings, tools, output definitions and the
     * already translated messages. Implementations must not mutate the given message nodes; they
     * are shared across turns.
     *
     * @param ctx      Neutral call context; carries the run mapper and the tool choice.
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
     * @param ctx  Neutral call context; carries the run mapper.
     * @param body Response JSON.
     * @return Neutral response; never null (throw on undecodable responses).
     */
    WireResponse decodeResponse(WireContext ctx, JsonNode body);

    /**
     * Decodes one SSE frame into a neutral stream event. Return null for frames the protocol
     * ignores (comments, keep-alives, unrelated named events).
     *
     * @param ctx   Neutral call context; carries the run mapper.
     * @param event Parsed SSE frame.
     * @return Neutral event, or null when the frame carries nothing relevant.
     */
    WireStreamEvent decodeStreamEvent(WireContext ctx, SseEvent event);

    /**
     * @param ctx Neutral call context.
     * @return Full endpoint URL for this call: base URL plus the protocol path.
     */
    String endpoint(WireContext ctx);

    /**
     * @return The codec translating {@link com.phonepe.sentinelai.core.agentmessages.AgentMessage}
     *         to this wire format. Stateless: translation takes the run mapper.
     */
    MessageCodec messageCodec();

    /**
     * Resolves the tool choice string for the run from the context tool choice and the output
     * generation mode. Shared helper for protocols whose tool choice is a string field.
     *
     * @param ctx Neutral call context.
     * @return Tool choice string for the wire.
     */
    default String resolveToolChoice(WireContext ctx) {
        return switch (ctx.getOutputGenerationMode()) {
            case TOOL_BASED -> switch (ctx.getToolChoice()) {
                case REQUIRED, DEFAULT -> "required";
                case AUTO -> "auto";
            };
            case STRUCTURED_OUTPUT -> switch (ctx.getToolChoice()) {
                case REQUIRED -> "required";
                case AUTO, DEFAULT -> "auto";
            };
        };
    }
}
