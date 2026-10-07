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
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import static com.phonepe.sentinelai.models.openai.ResponsesFields.ARGUMENTS;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.CALL_ID;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.CONTENT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.DETAIL;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.FILENAME;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.FILE_DATA;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.IMAGE_URL;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.INPUT_FILE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.INPUT_IMAGE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ITEM_FUNCTION_CALL;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ITEM_FUNCTION_CALL_OUTPUT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ITEM_MESSAGE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.MARKER_COMPACTED;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.MARKER_RESPONSE_ID;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.MARKER_RUN_ID;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.NAME;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.OUTPUT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ROLE;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ROLE_ASSISTANT;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ROLE_SYSTEM;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.ROLE_USER;
import static com.phonepe.sentinelai.models.openai.ResponsesFields.TYPE;

/**
 * Translates {@link AgentMessage}s into OpenAI Responses {@code input} item JSON. Role messages
 * become plain {@code message} items with string content; user prompts with media become message
 * items with content-part arrays ({@code input_text}, {@code input_image}, {@code input_file}).
 * Assistant tool calls and tool responses become flat {@code function_call} and
 * {@code function_call_output} items. The system prompt becomes a message item the protocol lifts
 * into the top-level {@code instructions} field. Stateless: every call takes the run mapper.
 */
public class ResponsesMessageCodec implements MessageCodec {

    /**
     * Embeds internal chaining markers on nodes produced from agent responses that carry a
     * provider response id. The markers never reach the provider; the protocol strips them
     * while assembling the request body and uses them to resolve the chaining anchor.
     */
    private static void addChainMarkers(final ObjectNode node, final AgentResponse response) {
        final var responseId = response.getResponseId();
        if (responseId == null || responseId.isBlank()) {
            return;
        }
        node.put(MARKER_RESPONSE_ID, responseId);
        node.put(MARKER_RUN_ID, response.getRunId());
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
    public JsonNode translate(final AgentMessage message, final ObjectMapper mapper) {
        return message.accept(new AgentMessageVisitor<>() {
            @Override
            public ObjectNode visit(final AgentGenericMessage genericMessage) {
                return genericMessage.accept(new AgentGenericMessageVisitor<>() {
                    @Override
                    public ObjectNode visit(final GenericResource genericResource) {
                        return messageItem(mapper,
                                           roleOf(genericResource.getRole()),
                                           genericResource.getSerializedJson());
                    }

                    @Override
                    public ObjectNode visit(final GenericText genericText) {
                        return messageItem(mapper, roleOf(genericText.getRole()), genericText.getText());
                    }
                });
            }

            @Override
            public ObjectNode visit(final AgentRequest request) {
                return request.accept(new AgentRequestVisitor<>() {
                    @Override
                    public ObjectNode visit(final SystemPrompt systemPrompt) {
                        return messageItem(mapper, ROLE_SYSTEM, systemPrompt.getContent());
                    }

                    @Override
                    public ObjectNode visit(final ToolCallResponse toolCallResponse) {
                        final var node = mapper.createObjectNode();
                        node.put(TYPE, ITEM_FUNCTION_CALL_OUTPUT);
                        node.put(CALL_ID, toolCallResponse.getToolCallId());
                        node.put(OUTPUT, toolCallResponse.getResponse());
                        return node;
                    }

                    @Override
                    public ObjectNode visit(final UserPrompt userPrompt) {
                        final ObjectNode node = switch (userPrompt.getContentType()) {
                            case TEXT -> messageItem(mapper, ROLE_USER, withSentAt(userPrompt));
                            case AUDIO -> throw unsupported("Audio content");
                            case IMAGE_URL, IMAGE_DATA -> imageItem(mapper, userPrompt);
                            case FILE -> fileItem(mapper, userPrompt);
                            default -> throw new IllegalArgumentException(
                                                                          "Unexpected value: " + userPrompt
                                                                                  .getContentType());
                        };
                        if (userPrompt.isCompacted()) {
                            node.put(MARKER_COMPACTED, true);
                        }
                        return node;
                    }
                });
            }

            @Override
            public ObjectNode visit(final AgentResponse response) {
                return response.accept(new AgentResponseVisitor<>() {
                    @Override
                    public ObjectNode visit(final StructuredOutput structuredOutput) {
                        final var node = messageItem(mapper, ROLE_ASSISTANT, structuredOutput.getContent());
                        addChainMarkers(node, response);
                        return node;
                    }

                    @Override
                    public ObjectNode visit(final Text text) {
                        final var node = messageItem(mapper, ROLE_ASSISTANT, text.getContent());
                        addChainMarkers(node, response);
                        return node;
                    }

                    @Override
                    public ObjectNode visit(final ToolCall toolCall) {
                        final var node = mapper.createObjectNode();
                        node.put(TYPE, ITEM_FUNCTION_CALL);
                        node.put(CALL_ID, toolCall.getToolCallId());
                        node.put(NAME, toolCall.getToolName());
                        node.put(ARGUMENTS, toolCall.getArguments());
                        addChainMarkers(node, response);
                        return node;
                    }
                });
            }
        });
    }

    private ObjectNode fileItem(final ObjectMapper mapper, final UserPrompt userPrompt) {
        final var part = mapper.createObjectNode();
        part.put(TYPE, INPUT_FILE);
        final var file = mapper.createObjectNode();
        file.put(FILE_DATA, userPrompt.getContent());
        if (userPrompt.getFileName() != null) {
            file.put(FILENAME, userPrompt.getFileName());
        }
        part.set(INPUT_FILE, file);
        return mediaMessageItem(mapper, part);
    }

    private ObjectNode imageItem(final ObjectMapper mapper, final UserPrompt userPrompt) {
        final var part = mapper.createObjectNode();
        part.put(TYPE, INPUT_IMAGE);
        final var image = mapper.createObjectNode();
        image.put(IMAGE_URL, userPrompt.getContent());
        image.put(DETAIL, imageDetailOf(userPrompt.getImageDetail()));
        part.set(INPUT_IMAGE, image);
        return mediaMessageItem(mapper, part);
    }

    private ObjectNode mediaMessageItem(final ObjectMapper mapper, final ObjectNode part) {
        final var node = mapper.createObjectNode();
        node.put(TYPE, ITEM_MESSAGE);
        node.put(ROLE, ROLE_USER);
        final ArrayNode parts = mapper.createArrayNode();
        parts.add(part);
        node.set(CONTENT, parts);
        return node;
    }

    private ObjectNode messageItem(final ObjectMapper mapper, final String role, final String content) {
        final var node = mapper.createObjectNode();
        node.put(TYPE, ITEM_MESSAGE);
        node.put(ROLE, role);
        if (content != null) {
            node.put(CONTENT, content);
        }
        return node;
    }

    private UnsupportedOperationException unsupported(final String what) {
        return new UnsupportedOperationException(what + " is not supported in Responses message conversion");
    }
}
