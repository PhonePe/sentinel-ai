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

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
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
     * Default endpoint path prefix used when the provider declares none, for example
     * {@code https://api.openai.com/v1/chat/completions}.
     */
    String DEFAULT_ENDPOINT_PREFIX = "/v1";

    // Field names shared by OpenAI style protocols
    String TEMPERATURE_FIELD = "temperature";
    String TOP_P_FIELD = "top_p";
    String TYPE_FIELD = "type";
    String NAME_FIELD = "name";
    String DESCRIPTION_FIELD = "description";
    String PARAMETERS_FIELD = "parameters";
    String STRICT_FIELD = "strict";
    String TYPE_FUNCTION_VALUE = "function";
    String TOOL_CHOICE_REQUIRED_VALUE = "required";
    String TOOL_CHOICE_AUTO_VALUE = "auto";

    /**
     * Deep merges {@code override} into {@code target}; override wins, nested objects merge.
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
     * Merges user extras into the request body last; deep merge, extras win. Unknown keys are
     * allowed by design (vLLM style servers add arbitrary request fields).
     */
    default void applyExtras(ObjectNode body, JsonNode extras) {
        if (extras == null || extras.isNull() || !extras.isObject()) {
            return;
        }
        deepMerge(body, extras);
    }

    /**
     * Applies the model settings to the request body; shared helper for OpenAI style protocols.
     */
    default void applyModelSettings(final WireContext ctx, final ModelSettings settings, final ObjectNode body) {
        if (settings == null) {
            return;
        }
        if (settings.getTemperature() != null) {
            body.put(TEMPERATURE_FIELD, settings.getTemperature().doubleValue());
        }
        if (settings.getTopP() != null) {
            body.put(TOP_P_FIELD, settings.getTopP().doubleValue());
        }
    }

    /**
     * Builds the flat tool array for protocols with a flat tools list.
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
                    toolNode.put(TYPE_FIELD, TYPE_FUNCTION_VALUE);
                    toolNode.put(NAME_FIELD, definition.getId());
                    toolNode.put(DESCRIPTION_FIELD, definition.getDescription());
                    toolNode.set(PARAMETERS_FIELD, tool.accept(parameterMapper));
                    toolNode.put(STRICT_FIELD, definition.isStrictSchema());
                    toolArray.add(toolNode);
                });
        return toolArray;
    }

    /**
     * Builds the full request body for one turn; must not mutate the given message nodes.
     */
    ObjectNode buildRequestBody(WireContext ctx, List<JsonNode> messages);

    /**
     * Classifies an HTTP error into a neutral {@link ErrorType}.
     */
    ErrorType classifyError(int status, JsonNode errorBody);

    /**
     * Decodes a single-turn blocking response body; never returns null.
     */
    WireResponse decodeResponse(WireContext ctx, JsonNode body);

    /**
     * Decodes one SSE frame into its neutral stream events in frame order; a frame may carry
     * several events. Return an empty list for frames the protocol ignores.
     */
    List<WireStreamEvent> decodeStreamEvent(WireContext ctx, SseEvent event);

    /**
     * @return Full endpoint URL for this call: base URL plus the effective endpoint prefix
     *         plus the protocol path.
     */
    String endpoint(WireContext ctx);

    /**
     * @return The stateless codec translating {@link AgentMessage} to this wire format.
     */
    MessageCodec messageCodec();

    /**
     * Resolves the tool choice string for the run; shared helper for string tool choice fields.
     */
    default String resolveToolChoice(WireContext ctx) {
        return switch (ctx.getOutputGenerationMode()) {
            case TOOL_BASED -> switch (ctx.getToolChoice()) {
                case REQUIRED, DEFAULT -> TOOL_CHOICE_REQUIRED_VALUE;
                case AUTO -> TOOL_CHOICE_AUTO_VALUE;
            };
            case STRUCTURED_OUTPUT -> switch (ctx.getToolChoice()) {
                case REQUIRED -> TOOL_CHOICE_REQUIRED_VALUE;
                case AUTO, DEFAULT -> TOOL_CHOICE_AUTO_VALUE;
            };
        };
    }
}
