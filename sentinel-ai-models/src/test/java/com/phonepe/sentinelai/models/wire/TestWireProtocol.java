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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentResponse;
import com.phonepe.sentinelai.core.agentmessages.AgentResponseVisitor;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericResource;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.tools.ParameterMapper;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Test stub of a {@link WireProtocol} speaking the OpenAI Chat Completions wire format over plain
 * Jackson nodes. Stateless: the run mapper arrives through the {@link WireContext}. Do not use
 * this class outside tests.
 */
public class TestWireProtocol implements WireProtocol {

    private static String imageDetailOf(final MediaTypes.ImageDetail detail) {
        return switch (detail) {
            case AUTO -> "auto";
            case LOW -> "low";
            case HIGH -> "high";
        };
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

    private static String roleOf(final AgentGenericMessage.Role role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL_CALL -> throw new UnsupportedOperationException("Tool calls are unsupported here");
        };
    }

    private static String textOrNull(final JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static String withSentAt(final UserPrompt userPrompt) {
        final var sentAt = userPrompt.getSentAt();
        if (sentAt == null) {
            return userPrompt.getContent();
        }
        final var isoUtc = sentAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME);
        return "<sentAt>" + isoUtc + "</sentAt>\n" + userPrompt.getContent();
    }

    @Override
    public void applyModelSettings(final WireContext ctx, final ModelSettings settings, final ObjectNode body) {
        if (settings == null) {
            return;
        }
        if (settings.getMaxTokens() != null) {
            body.put("max_completion_tokens", settings.getMaxTokens());
        }
        if (settings.getTemperature() != null) {
            body.put("temperature", settings.getTemperature().doubleValue());
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
            final var logitBias = ctx.getMapper().createObjectNode();
            settings.getLogitBias().forEach(logitBias::put);
            body.set("logit_bias", logitBias);
        }
        if (settings.getReasoning() != null) {
            body.put("reasoning_effort", settings.getReasoning().name().toLowerCase());
        }
    }

    @Override
    public ObjectNode buildRequestBody(final WireContext ctx, final List<JsonNode> messages) {
        final var mapper = ctx.getMapper();
        final var body = mapper.createObjectNode();
        final var messageArray = mapper.createArrayNode();
        messages.forEach(messageArray::add);
        body.set("messages", messageArray);
        body.put("model", ctx.effectiveModelId());
        body.put("n", 1);
        if (ctx.getUserId() != null && !ctx.getUserId().isEmpty()) {
            body.put("user", ctx.getUserId());
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
            responseFormat.put("type", "json_schema");
            final var jsonSchema = mapper.createObjectNode();
            jsonSchema.put("name", "model_output");
            jsonSchema.set("schema", ctx.getOutputSchema());
            jsonSchema.put("strict", true);
            responseFormat.set("json_schema", jsonSchema);
            body.set("response_format", responseFormat);
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
        final var choices = body.get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Empty choices in completion response");
        }
        final var choice = choices.get(0);
        final var message = choice.get("message");
        final var toolCalls = new ArrayList<WireToolCall>();
        if (message != null && message.has("tool_calls")) {
            message.get("tool_calls").forEach(call -> {
                final var function = call.get("function");
                toolCalls.add(new WireToolCall(textOrNull(call.get("id")),
                                               textOrNull(function.get("name")),
                                               textOrNull(function.get("arguments"))));
            });
        }
        return new WireResponse(normalizeFinishReason(textOrNull(choice.get("finish_reason"))),
                                message == null ? null : textOrNull(message.get("content")),
                                message == null ? null : textOrNull(message.get("reasoning_content")),
                                message == null ? null : textOrNull(message.get("refusal")),
                                toolCalls,
                                decodeUsage(body.get("usage")));
    }

    @Override
    public List<WireStreamEvent> decodeStreamEvent(final WireContext ctx, final SseEvent event) {
        final JsonNode body;
        try {
            body = ctx.getMapper().readTree(event.getData());
        }
        catch (final Exception e) {
            return List.of();
        }
        final var events = new ArrayList<WireStreamEvent>();
        final var choices = body.get("choices");
        if (choices != null && !choices.isEmpty()) {
            final var choice = choices.get(0);
            final var delta = choice.get("delta");
            if (delta != null) {
                final var content = textOrNull(delta.get("content"));
                if (content != null && !content.isEmpty()) {
                    events.add(new WireStreamEvent.ContentDelta(content));
                }
                final var reasoning = textOrNull(delta.get("reasoning_content"));
                if (reasoning != null && !reasoning.isEmpty()) {
                    events.add(new WireStreamEvent.ReasoningDelta(reasoning));
                }
                final var toolCalls = delta.get("tool_calls");
                if (toolCalls != null && toolCalls.isArray()) {
                    toolCalls.forEach(call -> {
                        final var function = call.get("function");
                        events.add(new WireStreamEvent.ToolCallDelta(
                                                                     call.path("index").asInt(0),
                                                                     textOrNull(call.get("id")),
                                                                     function == null ? null : textOrNull(function.get(
                                                                                                                       "name")),
                                                                     function == null ? null : textOrNull(function.get(
                                                                                                                       "arguments"))));
                    });
                }
            }
            final var finishReason = textOrNull(choice.get("finish_reason"));
            if (finishReason != null) {
                // The finish event carries the usage when both arrive on the same frame.
                // The usage event stays out of the list so the loop does not merge it twice.
                events.add(new WireStreamEvent.StreamFinishEvent(normalizeFinishReason(finishReason),
                                                                 delta == null
                                                                         ? null
                                                                         : textOrNull(delta.get("refusal")),
                                                                 decodeUsage(body.get("usage"))));
                return events;
            }
        }
        final var usage = decodeUsage(body.get("usage"));
        if (usage != null) {
            events.add(new WireStreamEvent.StreamUsageEvent(usage));
        }
        return events;
    }

    @Override
    public String endpoint(final WireContext ctx) {
        return ctx.getBaseUrl() + "/chat/completions";
    }

    @Override
    public MessageCodec messageCodec() {
        return this::translateMessage;
    }

    private void addTools(final WireContext ctx, final ObjectNode body) {
        final var mapper = ctx.getMapper();
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
                    toolNode.put("type", "function");
                    final var function = mapper.createObjectNode();
                    function.put("name", definition.getId());
                    function.put("description", definition.getDescription());
                    function.set("parameters", tool.accept(parameterMapper));
                    function.put("strict", definition.isStrictSchema());
                    toolNode.set("function", function);
                    toolArray.add(toolNode);
                });
        body.set("tools", toolArray);
        body.put("tool_choice", resolveToolChoice(ctx));
        final var parallelToolCalls = ctx.getModelSettings() == null
                || Objects.requireNonNullElse(ctx.getModelSettings().getParallelToolCalls(), true);
        body.put("parallel_tool_calls", parallelToolCalls);
    }

    private ObjectNode contentPartMessage(final ObjectMapper mapper,
                                          final String partType,
                                          final String payloadField,
                                          final String payload,
                                          final String metaField,
                                          final String metaValue) {
        final var node = roleMessage(mapper, "user", null);
        final var parts = mapper.createArrayNode();
        final var part = mapper.createObjectNode();
        part.put("type", partType);
        final var payloadNode = mapper.createObjectNode();
        payloadNode.put(payloadField, payload);
        payloadNode.put(metaField, metaValue);
        part.set(partType, payloadNode);
        parts.add(part);
        node.set("content", parts);
        return node;
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

    private ObjectNode roleMessage(final ObjectMapper mapper, final String role, final String content) {
        final var node = mapper.createObjectNode();
        node.put("role", role);
        if (content != null) {
            node.put("content", content);
        }
        return node;
    }

    /**
     * Translates one message into a Chat Completions message node, mirroring the old
     * {@code OpenAIMessageUtils#convertIndividualMessageToOpenAIFormat}.
     */
    private ObjectNode translateMessage(final AgentMessage message, final ObjectMapper mapper) {
        return message.accept(new AgentMessageVisitor<>() {
            @Override
            public ObjectNode visit(final AgentGenericMessage genericMessage) {
                return genericMessage.accept(new AgentGenericMessageVisitor<>() {
                    @Override
                    public ObjectNode visit(final GenericResource genericResource) {
                        return roleMessage(mapper,
                                           roleOf(genericResource.getRole()),
                                           genericResource.getSerializedJson());
                    }

                    @Override
                    public ObjectNode visit(final GenericText genericText) {
                        return roleMessage(mapper, roleOf(genericText.getRole()), genericText.getText());
                    }
                });
            }

            @Override
            public ObjectNode visit(final AgentRequest request) {
                return request.accept(new AgentRequestVisitor<>() {
                    @Override
                    public ObjectNode visit(final SystemPrompt systemPrompt) {
                        return roleMessage(mapper, "system", systemPrompt.getContent());
                    }

                    @Override
                    public ObjectNode visit(final ToolCallResponse toolCallResponse) {
                        final var node = mapper.createObjectNode();
                        node.put("role", "tool");
                        node.put("tool_call_id", toolCallResponse.getToolCallId());
                        node.put("content", toolCallResponse.getResponse());
                        return node;
                    }

                    @Override
                    public ObjectNode visit(final UserPrompt userPrompt) {
                        return switch (userPrompt.getContentType()) {
                            case TEXT -> roleMessage(mapper, "user", withSentAt(userPrompt));
                            case AUDIO -> contentPartMessage(mapper,
                                                             "input_audio",
                                                             "data",
                                                             userPrompt.getContent(),
                                                             "format",
                                                             userPrompt.getAudioFormat().name().toLowerCase());
                            case IMAGE_URL, IMAGE_DATA -> contentPartMessage(mapper,
                                                                             "image_url",
                                                                             "url",
                                                                             userPrompt.getContent(),
                                                                             "detail",
                                                                             imageDetailOf(userPrompt
                                                                                     .getImageDetail()));
                            case FILE -> throw new UnsupportedOperationException(
                                                                                 "File content type is not supported");
                        };
                    }
                });
            }

            @Override
            public ObjectNode visit(final AgentResponse response) {
                return response.accept(new AgentResponseVisitor<>() {
                    @Override
                    public ObjectNode visit(final StructuredOutput structuredOutput) {
                        return roleMessage(mapper, "assistant", structuredOutput.getContent());
                    }

                    @Override
                    public ObjectNode visit(final Text text) {
                        return roleMessage(mapper, "assistant", text.getContent());
                    }

                    @Override
                    public ObjectNode visit(final ToolCall toolCall) {
                        final var node = roleMessage(mapper, "assistant", null);
                        final var calls = mapper.createArrayNode();
                        final var call = mapper.createObjectNode();
                        call.put("id", toolCall.getToolCallId());
                        call.put("type", "function");
                        final var function = mapper.createObjectNode();
                        function.put("name", toolCall.getToolName());
                        function.put("arguments", toolCall.getArguments());
                        call.set("function", function);
                        calls.add(call);
                        node.set("tool_calls", calls);
                        return node;
                    }
                });
            }
        });
    }
}
