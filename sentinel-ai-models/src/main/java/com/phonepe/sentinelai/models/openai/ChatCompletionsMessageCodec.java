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
import com.phonepe.sentinelai.models.wire.MessageCodec;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.ARGUMENTS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.CONTENT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.DATA;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.DETAIL;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.FORMAT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.FUNCTION;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.IMAGE_URL;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.INPUT_AUDIO;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.NAME;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.ROLE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.ROLE_ASSISTANT;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.ROLE_SYSTEM;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.ROLE_TOOL;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.ROLE_USER;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TOOL_CALLS;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TOOL_CALL_ID;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TYPE;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.TYPE_FUNCTION;
import static com.phonepe.sentinelai.models.openai.ChatCompletionsFields.URL;

/**
 * Translates {@link AgentMessage}s into OpenAI Chat Completions message JSON. Ports the conversion
 * of the previous {@code OpenAIMessageUtils}: role messages, tool messages, content parts for
 * audio and image user prompts, the {@code <sentAt>} prefix on text user prompts and assistant
 * tool call messages.
 */
public class ChatCompletionsMessageCodec implements MessageCodec {

    private final ObjectMapper mapper;

    public ChatCompletionsMessageCodec(final ObjectMapper mapper) {
        this.mapper = mapper;
    }

    private static String imageDetailOf(final MediaTypes.ImageDetail detail) {
        return switch (detail) {
            case AUTO -> "auto";
            case LOW -> "low";
            case HIGH -> "high";
        };
    }

    private static String roleOf(final AgentGenericMessage.Role role) {
        return switch (role) {
            case SYSTEM -> ROLE_SYSTEM;
            case USER -> ROLE_USER;
            case ASSISTANT -> ROLE_ASSISTANT;
            case TOOL_CALL -> throw new UnsupportedOperationException(
                                                                      "Tool calls are unsupported in this context");
        };
    }

    /**
     * Renders a {@link UserPrompt} into the text sent to the model, prefixing it with an absolute
     * send-time so the model knows when the user turn was sent. The timestamp comes from the
     * persisted {@code sentAt} of the message (captured once at creation), so replayed history
     * turns serialize byte-identically.
     */
    private static String withSentAt(final UserPrompt userPrompt) {
        final var sentAt = userPrompt.getSentAt();
        if (sentAt == null) {
            return userPrompt.getContent();
        }
        final var isoUtc = sentAt.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_DATE_TIME);
        return "<sentAt>" + isoUtc + "</sentAt>\n" + userPrompt.getContent();
    }

    @Override
    public JsonNode translate(final AgentMessage message) {
        return message.accept(new AgentMessageVisitor<>() {
            @Override
            public ObjectNode visit(final AgentGenericMessage genericMessage) {
                return genericMessage.accept(new AgentGenericMessageVisitor<>() {
                    @Override
                    public ObjectNode visit(final GenericResource genericResource) {
                        return roleMessage(roleOf(genericResource.getRole()),
                                           genericResource.getSerializedJson());
                    }

                    @Override
                    public ObjectNode visit(final GenericText genericText) {
                        return roleMessage(roleOf(genericText.getRole()), genericText.getText());
                    }
                });
            }

            @Override
            public ObjectNode visit(final AgentRequest request) {
                return request.accept(new AgentRequestVisitor<>() {
                    @Override
                    public ObjectNode visit(final SystemPrompt systemPrompt) {
                        return roleMessage(ROLE_SYSTEM, systemPrompt.getContent());
                    }

                    @Override
                    public ObjectNode visit(final ToolCallResponse toolCallResponse) {
                        final var node = mapper.createObjectNode();
                        node.put(ROLE, ROLE_TOOL);
                        node.put(TOOL_CALL_ID, toolCallResponse.getToolCallId());
                        node.put(CONTENT, toolCallResponse.getResponse());
                        return node;
                    }

                    @Override
                    public ObjectNode visit(final UserPrompt userPrompt) {
                        return switch (userPrompt.getContentType()) {
                            case TEXT -> roleMessage(ROLE_USER, withSentAt(userPrompt));
                            case AUDIO -> contentPartMessage(INPUT_AUDIO,
                                                             DATA,
                                                             userPrompt.getContent(),
                                                             FORMAT,
                                                             userPrompt.getAudioFormat()
                                                                     .name()
                                                                     .toLowerCase());
                            case IMAGE_URL, IMAGE_DATA -> contentPartMessage(IMAGE_URL,
                                                                             URL,
                                                                             userPrompt.getContent(),
                                                                             DETAIL,
                                                                             imageDetailOf(
                                                                                           userPrompt
                                                                                                   .getImageDetail()));
                            case FILE -> throw new UnsupportedOperationException(
                                                                                 "File content type is not supported in Chat Completions message conversion");
                            default -> throw new IllegalArgumentException(
                                                                          "Unexpected value: " + userPrompt
                                                                                  .getContentType());
                        };
                    }
                });
            }

            @Override
            public ObjectNode visit(final AgentResponse response) {
                return response.accept(new AgentResponseVisitor<>() {
                    @Override
                    public ObjectNode visit(final StructuredOutput structuredOutput) {
                        return roleMessage(ROLE_ASSISTANT, structuredOutput.getContent());
                    }

                    @Override
                    public ObjectNode visit(final Text text) {
                        return roleMessage(ROLE_ASSISTANT, text.getContent());
                    }

                    @Override
                    public ObjectNode visit(final ToolCall toolCall) {
                        final var node = roleMessage(ROLE_ASSISTANT, null);
                        final var calls = mapper.createArrayNode();
                        final var call = mapper.createObjectNode();
                        call.put("id", toolCall.getToolCallId());
                        call.put(TYPE, TYPE_FUNCTION);
                        final var function = mapper.createObjectNode();
                        function.put(NAME, toolCall.getToolName());
                        function.put(ARGUMENTS, toolCall.getArguments());
                        call.set(FUNCTION, function);
                        calls.add(call);
                        node.set(TOOL_CALLS, calls);
                        return node;
                    }
                });
            }
        });
    }

    private ObjectNode contentPartMessage(final String partType,
                                          final String payloadField,
                                          final String payload,
                                          final String metaField,
                                          final String metaValue) {
        final var node = roleMessage(ROLE_USER, null);
        final var parts = mapper.createArrayNode();
        final var part = mapper.createObjectNode();
        part.put(TYPE, partType);
        final var payloadNode = mapper.createObjectNode();
        payloadNode.put(payloadField, payload);
        payloadNode.put(metaField, metaValue);
        part.set(partType, payloadNode);
        parts.add(part);
        node.set(CONTENT, parts);
        return node;
    }

    private ObjectNode roleMessage(final String role, final String content) {
        final var node = mapper.createObjectNode();
        node.put(ROLE, role);
        if (content != null) {
            node.put(CONTENT, content);
        }
        return node;
    }
}
