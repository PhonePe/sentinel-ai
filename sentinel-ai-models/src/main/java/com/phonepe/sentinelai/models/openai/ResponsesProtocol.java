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

package com.phonepe.sentinelai.models.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.tools.ParameterMapper;
import com.phonepe.sentinelai.models.ModelOptions;
import com.phonepe.sentinelai.models.transport.SseEvent;
import com.phonepe.sentinelai.models.wire.MessageCodec;
import com.phonepe.sentinelai.models.wire.WireContext;
import com.phonepe.sentinelai.models.wire.WireProtocol;
import com.phonepe.sentinelai.models.wire.WireResponse;
import com.phonepe.sentinelai.models.wire.WireStreamEvent;
import com.phonepe.sentinelai.models.wire.WireToolCall;
import com.phonepe.sentinelai.models.wire.WireUsage;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.phonepe.sentinelai.models.openai.ResponsesFields.ARGUMENTS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.CACHED_TOKENS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.CALL_ID;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.CONTENT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.DELTA;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.DESCRIPTION;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.EFFORT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ERROR;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.EVENT_FUNCTION_CALL_ARGUMENTS_DELTA;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.EVENT_OUTPUT_ITEM_ADDED;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.EVENT_OUTPUT_TEXT_DELTA;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.EVENT_REASONING_DELTA;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.EVENT_REASONING_SUMMARY_TEXT_DELTA;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.EVENT_REFUSAL_DELTA;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.EVENT_RESPONSE_COMPLETED;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.EVENT_RESPONSE_FAILED;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.EVENT_RESPONSE_INCOMPLETE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.FORMAT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.INPUT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.INPUT_TOKENS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.INPUT_TOKEN_DETAILS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.INSTRUCTIONS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ITEM;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ITEM_FUNCTION_CALL;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ITEM_MESSAGE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.JSON_SCHEMA;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.MAX_OUTPUT_TOKENS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.MODEL;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.NAME;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.OUTPUT_INDEX;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.OUTPUT_TEXT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.OUTPUT_TOKENS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.OUTPUT_TOKEN_DETAILS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.PARAMETERS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.REASONING;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.REASONING_TOKENS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.REFUSAL;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.RESPONSE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.RESPONSE_OUTPUT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ROLE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ROLE_SYSTEM;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.SCHEMA;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.STATUS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.STATUS_COMPLETED;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.STATUS_INCOMPLETE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.STORE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.STREAM;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.STRICT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.TEMPERATURE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.TEXT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.TEXT_FIELD;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.TOOLS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.TOOL_CHOICE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.TOP_P;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.TOTAL_TOKENS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.TYPE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.TYPE_FUNCTION;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.USER;

/**
 * {@link WireProtocol} for the OpenAI Responses wire format. Assembles the request (flat function
 * tools, top-level instructions, structured output through {@code text.format.json_schema}) and
 * decodes blocking responses and named SSE events into the neutral wire types.
 */
@Slf4j
public class ResponsesProtocol implements WireProtocol {

    /**
     * Bearer header sent for authentication when an API key is set.
     */
    /**
     * Bearer header sent for authentication when an API key is set.
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String ENDPOINT_PATH = "/responses";

    private final ObjectMapper mapper;
    private final ModelOptions modelOptions;
    private final String apiKey;
    private final MessageCodec messageCodec;

    /**
     * @param mapper       Jackson mapper used for all JSON work.
     * @param modelOptions Model options; null means {@link ModelOptions#DEFAULT}.
     * @param apiKey       API key sent as a Bearer token; null means no auth header (leave auth
     *                     to the transport or its interceptors).
     */
    public ResponsesProtocol(final ObjectMapper mapper,
                             final ModelOptions modelOptions,
                             final String apiKey) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.modelOptions = Objects.requireNonNullElse(modelOptions, ModelOptions.DEFAULT);
        this.apiKey = apiKey;
        this.messageCodec = new ResponsesMessageCodec(mapper);
    }

    private static Integer intOrNull(final JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private static String textOrNull(final JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    @Override
    public ObjectNode buildRequestBody(final WireContext ctx, final List<JsonNode> messages) {
        final var body = mapper.createObjectNode();
        final var inputArray = mapper.createArrayNode();
        messages.forEach(inputArray::add);
        body.set(INPUT, inputArray);
        body.put(MODEL, ctx.getModelName());
        final var instructions = liftInstructions(inputArray);
        if (instructions != null) {
            body.put(INSTRUCTIONS, instructions);
        }
        body.put(STORE, false);
        if (ctx.getUserId() != null && !ctx.getUserId().isEmpty()) {
            body.put(USER, ctx.getUserId());
        }
        applyModelSettings(ctx.getModelSettings(), body);
        final var toolsDisabled = ctx.getModelSettings() != null && Boolean.TRUE
                .equals(ctx.getModelSettings().getDisableTools());
        if (!toolsDisabled) {
            addTools(ctx, body);
        }
        if (ctx.getOutputGenerationMode().equals(OutputGenerationMode.STRUCTURED_OUTPUT)
                && ctx.getOutputSchema() != null) {
            final var text = mapper.createObjectNode();
            final var format = mapper.createObjectNode();
            format.put(TYPE, JSON_SCHEMA);
            final var jsonSchema = mapper.createObjectNode();
            jsonSchema.put(NAME, "model_output");
            jsonSchema.set(SCHEMA, ctx.getOutputSchema());
            jsonSchema.put(STRICT, true);
            format.set(JSON_SCHEMA, jsonSchema);
            text.set(FORMAT, format);
            body.set(TEXT, text);
        }
        if (ctx.isStreaming()) {
            body.put(STREAM, true);
        }
        applyExtras(body, ctx.getExtras());
        return body;
    }

    @Override
    public ErrorType classifyError(final int status, final JsonNode errorBody) {
        return switch (status) {
            case 429 -> ErrorType.MODEL_CALL_RATE_LIMIT_EXCEEDED;
            default -> ErrorType.MODEL_CALL_HTTP_FAILURE;
        };
    }

    @Override
    public WireResponse decodeResponse(final JsonNode body) {
        final var toolCalls = new ArrayList<WireToolCall>();
        final var content = new StringBuilder();
        var refusal = (String) null;
        final var output = body.get(RESPONSE_OUTPUT);
        if (output == null || !output.isArray()) {
            throw new IllegalStateException("""
                    Missing output items in Responses payload.
                    This usually indicates a response deserialization failure or a malformed response from the server""");
        }
        for (final JsonNode item : output) {
            final var type = textOrNull(item.get(TYPE));
            if (ITEM_FUNCTION_CALL.equals(type)) {
                toolCalls.add(new WireToolCall(textOrNull(item.get(CALL_ID)),
                                               textOrNull(item.get(NAME)),
                                               textOrNull(item.get(ARGUMENTS))));
            }
            else if (ITEM_MESSAGE.equals(type)) {
                final var messageContent = item.get(CONTENT);
                if (messageContent != null && messageContent.isArray()) {
                    for (final JsonNode part : messageContent) {
                        final var partType = textOrNull(part.get(TYPE));
                        if (OUTPUT_TEXT.equals(partType)) {
                            final var text = textOrNull(part.get(TEXT_FIELD));
                            if (text != null) {
                                content.append(text);
                            }
                        }
                        else if (REFUSAL.equals(partType)) {
                            refusal = textOrNull(part.get(REFUSAL));
                        }
                    }
                }
            }
        }
        return new WireResponse(finishReasonOf(body, !toolCalls.isEmpty()),
                                content.length() == 0 ? null : content.toString(),
                                null,
                                refusal,
                                toolCalls,
                                decodeUsage(body.get(ResponsesFields.USAGE)));
    }

    @Override
    public WireStreamEvent decodeStreamEvent(final SseEvent event) {
        final JsonNode body;
        try {
            body = mapper.readTree(event.data());
        }
        catch (final Exception e) {
            return null;
        }
        final var name = event.event() != null ? event.event() : textOrNull(body.get(TYPE));
        return switch (name) {
            case EVENT_OUTPUT_TEXT_DELTA -> contentDelta(body.get(DELTA),
                                                         body.get(TEXT_FIELD));
            case EVENT_REASONING_DELTA, EVENT_REASONING_SUMMARY_TEXT_DELTA -> contentDelta(body.get(DELTA),
                                                                                           body.get(TEXT_FIELD));
            case EVENT_REFUSAL_DELTA -> contentDelta(body.get(DELTA), body.get(REFUSAL));
            case EVENT_OUTPUT_ITEM_ADDED -> outputItemEvent(body.get(ITEM));
            case EVENT_FUNCTION_CALL_ARGUMENTS_DELTA -> new WireStreamEvent.ToolCallDelta(
                                                                                          intOrZero(body.get(
                                                                                                             OUTPUT_INDEX)),
                                                                                          null,
                                                                                          null,
                                                                                          textOrEmpty(body.get(DELTA)));
            case EVENT_RESPONSE_COMPLETED -> finalResponseEvent(body.get(RESPONSE));
            case EVENT_RESPONSE_FAILED, EVENT_RESPONSE_INCOMPLETE -> {
                final var response = body.get(RESPONSE);
                yield new WireStreamEvent.StreamFinishEvent(
                                                            finishReasonOf(response == null
                                                                    ? body
                                                                    : response,
                                                                           false),
                                                            null);
            }
            default -> null;
        };
    }

    @Override
    public String endpoint(final WireContext ctx) {
        return ctx.getBaseUrl() + ENDPOINT_PATH;
    }

    @Override
    public Map<String, String> headers(final WireContext ctx) {
        return apiKey == null
                ? Map.of()
                : Map.of(AUTHORIZATION_HEADER, "Bearer " + apiKey);
    }

    @Override
    public MessageCodec messageCodec() {
        return messageCodec;
    }

    private WireStreamEvent contentDelta(final JsonNode delta, final JsonNode text) {
        final var value = delta != null && !delta.isNull()
                ? delta.asText()
                : text == null || text.isNull() ? null
                : text.asText();
        return value == null || value.isEmpty() ? null : new WireStreamEvent.ContentDelta(value);
    }

    private WireStreamEvent outputItemEvent(final JsonNode item) {
        if (item == null) {
            return null;
        }
        if (ITEM_FUNCTION_CALL.equals(textOrNull(item.get(TYPE)))) {
            final var name = textOrNull(item.get(NAME));
            final var callId = textOrNull(item.get(CALL_ID));
            return name == null && callId == null
                    ? null
                    : new WireStreamEvent.ToolCallDelta(0, callId, name, null);
        }
        return null;
    }

    private WireStreamEvent finalResponseEvent(final JsonNode response) {
        if (response == null) {
            return null;
        }
        final var decoded = decodeResponse(response);
        if (decoded.finishReason() != null) {
            return new WireStreamEvent.StreamFinishEvent(decoded.finishReason(),
                                                         decoded.refusal(),
                                                         decoded.usage());
        }
        return null;
    }

    private int intOrZero(final JsonNode node) {
        return node == null || node.isNull() ? 0 : node.asInt();
    }

    private String textOrEmpty(final JsonNode node) {
        return node == null || node.isNull() ? "" : node.asText();
    }

    private String finishReasonOf(final JsonNode response, final boolean hasToolCalls) {
        final var error = textOrNull(response.get(ERROR));
        if (error != null && !error.isEmpty()) {
            return WireResponse.FinishReasons.REFUSED;
        }
        return switch (textOrNull(response.get(STATUS))) {
            case STATUS_COMPLETED -> hasToolCalls
                    ? WireResponse.FinishReasons.TOOL_CALLS
                    : WireResponse.FinishReasons.STOP;
            case STATUS_INCOMPLETE -> WireResponse.FinishReasons.LENGTH;
            default -> null;
        };
    }

    private String liftInstructions(final com.fasterxml.jackson.databind.node.ArrayNode inputArray) {
        String instructions = null;
        for (int i = 0; i < inputArray.size(); i++) {
            final var item = inputArray.get(i);
            if (ITEM_MESSAGE.equals(textOrNull(item.get(TYPE)))
                    && ROLE_SYSTEM.equals(textOrNull(item.get(ROLE)))) {
                instructions = textOrNull(item.get(CONTENT));
                inputArray.remove(i);
                break;
            }
        }
        return instructions;
    }

    private void addTools(final WireContext ctx, final ObjectNode body) {
        final var tools = ctx.getTools();
        if (tools.isEmpty()) {
            return;
        }
        final var parameterMapper = new ParameterMapper(mapper);
        final var toolArray = mapper.createArrayNode();
        tools.values()
                .stream()
                .sorted(Comparator.comparing(tool -> tool.getToolDefinition().getId()))
                .forEach(tool -> {
                    final var definition = tool.getToolDefinition();
                    final var toolNode = mapper.createObjectNode();
                    toolNode.put(TYPE, TYPE_FUNCTION);
                    toolNode.put(NAME, definition.getId());
                    toolNode.put(DESCRIPTION, definition.getDescription());
                    toolNode.set(PARAMETERS, tool.accept(parameterMapper));
                    toolNode.put(STRICT, definition.isStrictSchema());
                    toolArray.add(toolNode);
                });
        body.set(TOOLS, toolArray);
        body.put(TOOL_CHOICE, resolveToolChoice(ctx.getOutputGenerationMode()));
        final var parallelToolCalls = ctx.getModelSettings() == null
                || Objects.requireNonNullElse(ctx.getModelSettings().getParallelToolCalls(), true);
        body.put(ResponsesFields.PARALLEL_TOOL_CALLS, parallelToolCalls);
    }

    private void applyModelSettings(final ModelSettings settings, final ObjectNode body) {
        if (settings == null) {
            return;
        }
        if (settings.getMaxTokens() != null) {
            body.put(MAX_OUTPUT_TOKENS, settings.getMaxTokens());
        }
        if (settings.getTemperature() != null) {
            body.put(TEMPERATURE, settings.getTemperature().doubleValue());
        }
        if (settings.getTopP() != null) {
            body.put(TOP_P, settings.getTopP().doubleValue());
        }
        if (settings.getReasoning() != null) {
            final var reasoning = mapper.createObjectNode();
            reasoning.put(EFFORT, settings.getReasoning().name().toLowerCase());
            body.set(REASONING, reasoning);
        }
    }

    private WireUsage decodeUsage(final JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return null;
        }
        final var inputDetails = usage.get(INPUT_TOKEN_DETAILS);
        final var outputDetails = usage.get(OUTPUT_TOKEN_DETAILS);
        return new WireUsage(intOrNull(usage.get(INPUT_TOKENS)),
                             intOrNull(usage.get(OUTPUT_TOKENS)),
                             intOrNull(usage.get(TOTAL_TOKENS)),
                             null,
                             inputDetails == null ? null : intOrNull(inputDetails.get(CACHED_TOKENS)),
                             null,
                             outputDetails == null ? null : intOrNull(outputDetails.get(REASONING_TOKENS)));
    }

    private String resolveToolChoice(final OutputGenerationMode mode) {
        return switch (mode) {
            case TOOL_BASED -> switch (modelOptions.getToolChoice()) {
                case REQUIRED, DEFAULT -> ResponsesFields.TOOL_CHOICE_REQUIRED;
                case AUTO -> ResponsesFields.TOOL_CHOICE_AUTO;
            };
            case STRUCTURED_OUTPUT -> switch (modelOptions.getToolChoice()) {
                case REQUIRED -> {
                    log.warn("Model is configured for STRUCTURED_OUTPUT generation mode, "
                            + "but tool choice is set to REQUIRED. This might lead to infinite tool-call loops");
                    yield ResponsesFields.TOOL_CHOICE_REQUIRED;
                }
                case AUTO, DEFAULT -> ResponsesFields.TOOL_CHOICE_AUTO;
            };
        };
    }
}
