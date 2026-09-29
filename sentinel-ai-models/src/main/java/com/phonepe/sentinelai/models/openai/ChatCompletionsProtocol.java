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
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.tools.ParameterMapper;
import com.phonepe.sentinelai.models.wire.MessageCodec;
import com.phonepe.sentinelai.models.wire.SseEvent;
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
import java.util.Objects;

import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.ARGUMENTS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.AUDIO_TOKENS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.CACHED_TOKENS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.CHOICES;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.COMPLETION_TOKENS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.COMPLETION_TOKENS_DETAILS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.CONTENT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.DELTA;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.DESCRIPTION;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.FINISH_REASON;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.FREQUENCY_PENALTY;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.FUNCTION;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.FUNCTION_CALL;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.ID;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.INDEX;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.JSON_SCHEMA;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.LOGIT_BIAS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.MAX_COMPLETION_TOKENS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.MESSAGE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.MESSAGES;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.MODEL;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.N;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.NAME;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.PARALLEL_TOOL_CALLS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.PARAMETERS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.PRESENCE_PENALTY;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.PROMPT_TOKENS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.PROMPT_TOKENS_DETAILS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.REASONING_CONTENT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.REASONING_EFFORT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.REASONING_TOKENS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.REFUSAL;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.RESPONSE_FORMAT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.SCHEMA;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.SEED;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.STREAM;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.STRICT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TEMPERATURE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TOOLS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TOOL_CALLS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TOOL_CHOICE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TOP_P;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TOTAL_TOKENS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TYPE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TYPE_FUNCTION;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.USAGE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.USER;

/**
 * {@link WireProtocol} for the OpenAI Chat Completions wire format.
 */
@Slf4j
public class ChatCompletionsProtocol implements WireProtocol {

    private static final String ENDPOINT_PATH = "/chat/completions";

    private final MessageCodec messageCodec;

    /**
     * Creates a stateless protocol. The run mapper comes through the {@link WireContext} on every
     * call; authentication is owned by the provider of the model, not the protocol.
     */
    public ChatCompletionsProtocol() {
        this.messageCodec = new ChatCompletionsMessageCodec();
    }

    private static Integer intOrNull(final JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private static String normalizeFinishReason(final String finishReason) {
        if (finishReason == null) {
            return null;
        }
        return switch (finishReason) {
            case WireResponse.FinishReasons.STOP -> WireResponse.FinishReasons.STOP;
            case WireResponse.FinishReasons.TOOL_CALLS, FUNCTION_CALL -> WireResponse.FinishReasons.TOOL_CALLS;
            case WireResponse.FinishReasons.LENGTH -> WireResponse.FinishReasons.LENGTH;
            case WireResponse.FinishReasons.CONTENT_FILTER -> WireResponse.FinishReasons.CONTENT_FILTER;
            default -> finishReason;
        };
    }

    private static String textOrNull(final JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    @Override
    public ObjectNode buildRequestBody(final WireContext ctx, final List<JsonNode> messages) {
        final var mapper = ctx.getMapper();
        final var body = mapper.createObjectNode();
        final var messageArray = mapper.createArrayNode();
        messages.forEach(messageArray::add);
        body.set(MESSAGES, messageArray);
        body.put(MODEL, ctx.effectiveModelId());
        body.put(N, 1);
        if (ctx.getUserId() != null && !ctx.getUserId().isEmpty()) {
            body.put(USER, ctx.getUserId());
        }
        applyModelSettings(ctx, ctx.getModelSettings(), body);
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
    public WireResponse decodeResponse(final WireContext ctx, final JsonNode body) {
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
                                decodeUsage(body.get(USAGE)));
    }

    @Override
    public WireStreamEvent decodeStreamEvent(final WireContext ctx, final SseEvent event) {
        final JsonNode body;
        try {
            body = ctx.getMapper().readTree(event.data());
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
                                                                     call.path(INDEX).asInt(0),
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
        final var usage = decodeUsage(body.get(USAGE));
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
        return ctx.getBaseUrl() + ENDPOINT_PATH;
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
        final var mapper = ctx.getMapper();
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
        body.put(TOOL_CHOICE, resolveToolChoice(ctx));
        final var parallelToolCalls = ctx.getModelSettings() == null
                || Objects.requireNonNullElse(ctx.getModelSettings().getParallelToolCalls(), true);
        body.put(PARALLEL_TOOL_CALLS, parallelToolCalls);
    }

    @Override
    public void applyModelSettings(final WireContext ctx, final ModelSettings settings, final ObjectNode body) {
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
            body.put(TOP_P, settings.getTopP().doubleValue());
        }
        if (settings.getSeed() != null) {
            body.put(SEED, settings.getSeed());
        }
        if (settings.getFrequencyPenalty() != null) {
            body.put(FREQUENCY_PENALTY, settings.getFrequencyPenalty().doubleValue());
        }
        if (settings.getPresencePenalty() != null) {
            body.put(PRESENCE_PENALTY, settings.getPresencePenalty().doubleValue());
        }
        if (settings.getLogitBias() != null && !settings.getLogitBias().isEmpty()) {
            final var logitBias = ctx.getMapper().createObjectNode();
            settings.getLogitBias().forEach(logitBias::put);
            body.set(LOGIT_BIAS, logitBias);
        }
        if (settings.getReasoning() != null) {
            body.put(REASONING_EFFORT, settings.getReasoning().name().toLowerCase());
        }
    }

    private WireUsage decodeUsage(final JsonNode usage) {
        if (usage == null || usage.isNull()) {
            return null;
        }
        final var promptDetails = usage.get(PROMPT_TOKENS_DETAILS);
        final var completionDetails = usage.get(COMPLETION_TOKENS_DETAILS);
        return new WireUsage(intOrNull(usage.get(PROMPT_TOKENS)),
                             intOrNull(usage.get(COMPLETION_TOKENS)),
                             intOrNull(usage.get(TOTAL_TOKENS)),
                             promptDetails == null ? null : intOrNull(promptDetails.get(AUDIO_TOKENS)),
                             promptDetails == null ? null : intOrNull(promptDetails.get(CACHED_TOKENS)),
                             completionDetails == null ? null : intOrNull(completionDetails.get(AUDIO_TOKENS)),
                             completionDetails == null ? null : intOrNull(completionDetails.get(REASONING_TOKENS)));
    }

    @Override
    public String resolveToolChoice(final WireContext ctx) {
        return switch (ctx.getOutputGenerationMode()) {
            case TOOL_BASED -> switch (ctx.getToolChoice()) {
                case REQUIRED, DEFAULT -> ChatCompletionsFields.TOOL_CHOICE_REQUIRED;
                case AUTO -> ChatCompletionsFields.TOOL_CHOICE_AUTO;
            };
            case STRUCTURED_OUTPUT -> switch (ctx.getToolChoice()) {
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
