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

import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.ARGUMENTS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.CHOICES;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.CONTENT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.DELTA;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.DESCRIPTION;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.FINISH_REASON;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.FUNCTION;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.ID;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.JSON_SCHEMA;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.MAX_COMPLETION_TOKENS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.MESSAGE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.MESSAGES;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.MODEL;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.N;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.NAME;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.PARALLEL_TOOL_CALLS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.PARAMETERS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.REASONING_CONTENT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.REASONING_EFFORT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.REFUSAL;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.RESPONSE_FORMAT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.SCHEMA;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.STREAM;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.STRICT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TEMPERATURE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TOOLS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TOOL_CALLS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TOOL_CHOICE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TYPE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TYPE_FUNCTION;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.USER;

/**
 * {@link WireProtocol} for the OpenAI Chat Completions wire format. Ports the request assembly and
 * the response/stream decoding of the previous {@code SimpleOpenAIModel}. Free-form extras from
 * {@link ModelOptions} merge into the request body last and fully override protocol-built fields.
 */
@Slf4j
public class ChatCompletionsProtocol implements WireProtocol {

    /**
     * Bearer header sent for authentication when an API key is set.
     */
    public static final String AUTHORIZATION_HEADER = "Authorization";

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
    public ChatCompletionsProtocol(final ObjectMapper mapper,
                                   final ModelOptions modelOptions,
                                   final String apiKey) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.modelOptions = Objects.requireNonNullElse(modelOptions, ModelOptions.DEFAULT);
        this.apiKey = apiKey;
        this.messageCodec = new ChatCompletionsMessageCodec(mapper);
    }

    private static Integer intOrNull(final JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private static String normalizeFinishReason(final String finishReason) {
        if (finishReason == null) {
            return null;
        }
        return switch (finishReason) {
            case "stop" -> WireResponse.FinishReasons.STOP;
            case "tool_calls", "function_call" -> WireResponse.FinishReasons.TOOL_CALLS;
            case "length" -> WireResponse.FinishReasons.LENGTH;
            case "content_filter" -> WireResponse.FinishReasons.CONTENT_FILTER;
            default -> finishReason;
        };
    }

    private static String textOrNull(final JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    @Override
    public ObjectNode buildRequestBody(final WireContext ctx, final List<JsonNode> messages) {
        final var body = mapper.createObjectNode();
        final var messageArray = mapper.createArrayNode();
        messages.forEach(messageArray::add);
        body.set(MESSAGES, messageArray);
        body.put(MODEL, ctx.getModelName());
        body.put(N, 1);
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
            final var responseFormat = mapper.createObjectNode();
            responseFormat.put(TYPE, JSON_SCHEMA);
            final var jsonSchema = mapper.createObjectNode();
            jsonSchema.put(NAME, "model_output");
            jsonSchema.set(SCHEMA, ctx.getOutputSchema());
            jsonSchema.put(STRICT, true);
            responseFormat.set(JSON_SCHEMA, jsonSchema);
            body.set(RESPONSE_FORMAT, responseFormat);
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
        final var choices = body.get(CHOICES);
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("""
                    Empty choices in completion response.
                    This usually indicates a response deserialization failure or a malformed response from the server""");
        }
        final var choice = choices.get(0);
        final var message = choice.get(MESSAGE);
        final var toolCalls = new ArrayList<WireToolCall>();
        if (message != null && message.has(TOOL_CALLS)) {
            message.get(TOOL_CALLS).forEach(call -> {
                final var function = call.get(FUNCTION);
                toolCalls.add(new WireToolCall(textOrNull(call.get(ID)),
                                               function == null ? null : textOrNull(function.get(NAME)),
                                               function == null ? null : textOrNull(function.get(ARGUMENTS))));
            });
        }
        return new WireResponse(normalizeFinishReason(textOrNull(choice.get(FINISH_REASON))),
                                message == null ? null : textOrNull(message.get(CONTENT)),
                                message == null ? null : textOrNull(message.get(REASONING_CONTENT)),
                                message == null ? null : textOrNull(message.get(REFUSAL)),
                                toolCalls,
                                decodeUsage(body.get("usage")));
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
        final var events = new ArrayList<WireStreamEvent>();
        final var choices = body.get(CHOICES);
        if (choices != null && !choices.isEmpty()) {
            final var choice = choices.get(0);
            final var delta = choice.get(DELTA);
            if (delta != null) {
                final var content = textOrNull(delta.get(CONTENT));
                if (content != null && !content.isEmpty()) {
                    events.add(new WireStreamEvent.ContentDelta(content));
                }
                final var reasoning = textOrNull(delta.get(REASONING_CONTENT));
                if (reasoning != null && !reasoning.isEmpty()) {
                    events.add(new WireStreamEvent.ReasoningDelta(reasoning));
                }
                final var toolCalls = delta.get(TOOL_CALLS);
                if (toolCalls != null && toolCalls.isArray()) {
                    toolCalls.forEach(call -> {
                        final var function = call.get(FUNCTION);
                        events.add(new WireStreamEvent.ToolCallDelta(
                                                                     call.path("index").asInt(0),
                                                                     textOrNull(call.get(ID)),
                                                                     function == null ? null : textOrNull(function.get(
                                                                                                                       NAME)),
                                                                     function == null ? null : textOrNull(function.get(
                                                                                                                       ARGUMENTS))));
                    });
                }
            }
            final var finishReason = textOrNull(choice.get(FINISH_REASON));
            if (finishReason != null) {
                events.add(new WireStreamEvent.StreamFinishEvent(normalizeFinishReason(finishReason),
                                                                 delta == null
                                                                         ? null
                                                                         : textOrNull(delta.get(REFUSAL))));
            }
        }
        final var usage = decodeUsage(body.get("usage"));
        if (usage != null) {
            events.add(new WireStreamEvent.StreamUsageEvent(usage));
        }
        if (events.isEmpty()) {
            return null;
        }
        // One neutral event per frame. Finish wins over usage; the fixtures never combine them.
        return events.get(events.size() - 1);
    }

    @Override
    public String endpoint(final WireContext ctx) {
        return ctx.getBaseUrl() + "/chat/completions";
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
                    final var function = mapper.createObjectNode();
                    function.put(NAME, definition.getId());
                    function.put(DESCRIPTION, definition.getDescription());
                    function.set(PARAMETERS, tool.accept(parameterMapper));
                    function.put(STRICT, definition.isStrictSchema());
                    toolNode.set(FUNCTION, function);
                    toolArray.add(toolNode);
                });
        body.set(TOOLS, toolArray);
        body.put(TOOL_CHOICE, resolveToolChoice(ctx.getOutputGenerationMode()));
        final var parallelToolCalls = ctx.getModelSettings() == null
                || Objects.requireNonNullElse(ctx.getModelSettings().getParallelToolCalls(), true);
        body.put(PARALLEL_TOOL_CALLS, parallelToolCalls);
    }

    private void applyModelSettings(final ModelSettings settings, final ObjectNode body) {
        if (settings == null) {
            return;
        }
        if (settings.getMaxTokens() != null) {
            body.put(MAX_COMPLETION_TOKENS, settings.getMaxTokens());
        }
        if (settings.getTemperature() != null) {
            body.put(TEMPERATURE, settings.getTemperature().doubleValue());
        }
        if (settings.getTopP() != null) {
            body.put("top_p", settings.getTopP().doubleValue());
        }
        if (settings.getSeed() != null) {
            body.put("seed", settings.getSeed());
        }
        if (settings.getFrequencyPenalty() != null) {
            body.put("frequency_penalty", settings.getFrequencyPenalty().doubleValue());
        }
        if (settings.getPresencePenalty() != null) {
            body.put("presence_penalty", settings.getPresencePenalty().doubleValue());
        }
        if (settings.getLogitBias() != null && !settings.getLogitBias().isEmpty()) {
            final var logitBias = mapper.createObjectNode();
            settings.getLogitBias().forEach(logitBias::put);
            body.set("logit_bias", logitBias);
        }
        if (settings.getReasoning() != null) {
            body.put(REASONING_EFFORT, settings.getReasoning().name().toLowerCase());
        }
    }

    private WireUsage decodeUsage(final JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return null;
        }
        final var promptDetails = usage.get("prompt_tokens_details");
        final var completionDetails = usage.get("completion_tokens_details");
        return new WireUsage(intOrNull(usage.get("prompt_tokens")),
                             intOrNull(usage.get("completion_tokens")),
                             intOrNull(usage.get("total_tokens")),
                             promptDetails == null ? null : intOrNull(promptDetails.get("audio_tokens")),
                             promptDetails == null ? null : intOrNull(promptDetails.get("cached_tokens")),
                             completionDetails == null ? null : intOrNull(completionDetails.get("audio_tokens")),
                             completionDetails == null ? null : intOrNull(completionDetails.get("reasoning_tokens")));
    }

    private String resolveToolChoice(final OutputGenerationMode mode) {
        return switch (mode) {
            case TOOL_BASED -> switch (modelOptions.getToolChoice()) {
                case REQUIRED, DEFAULT -> ChatCompletionsFields.TOOL_CHOICE_REQUIRED;
                case AUTO -> ChatCompletionsFields.TOOL_CHOICE_AUTO;
            };
            case STRUCTURED_OUTPUT -> switch (modelOptions.getToolChoice()) {
                case REQUIRED -> {
                    log.warn("Model is configured for STRUCTURED_OUTPUT generation mode, "
                            + "but tool choice is set to REQUIRED. This might lead to infinite tool-call loops");
                    yield ChatCompletionsFields.TOOL_CHOICE_REQUIRED;
                }
                case AUTO, DEFAULT -> ChatCompletionsFields.TOOL_CHOICE_AUTO;
            };
        };
    }
}
