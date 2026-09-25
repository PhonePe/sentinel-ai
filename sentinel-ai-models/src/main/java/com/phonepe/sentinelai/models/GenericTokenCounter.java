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

package com.phonepe.sentinelai.models;

import com.google.common.base.Strings;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentRequest;
import com.phonepe.sentinelai.core.agentmessages.AgentRequestVisitor;
import com.phonepe.sentinelai.core.agentmessages.AgentResponse;
import com.phonepe.sentinelai.core.agentmessages.AgentResponseVisitor;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericResource;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;

import java.util.List;
import java.util.Objects;

/**
 * Generic token counter working directly on neutral {@link AgentMessage}s. Same estimation
 * heuristic as the previous OpenAI DTO based counter, but without the provider DTO detour: the
 * roles are the neutral roles and the token costs are the same. Token counts remain estimates
 * by design.
 */
public class GenericTokenCounter implements TokenCounter {

    private static final String ROLE_SYSTEM = "system";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String ROLE_TOOL = "tool";

    private static final class Counter implements AgentMessageVisitor<MessageCount> {

        private final Encoding encoder;
        private final TokenCountingConfig config;

        Counter(final Encoding encoder, final TokenCountingConfig config) {
            this.encoder = encoder;
            this.config = config;
        }

        private static String roleOf(final AgentGenericMessage.Role role) {
            return switch (role) {
                case SYSTEM -> ROLE_SYSTEM;
                case USER -> ROLE_USER;
                case ASSISTANT -> ROLE_ASSISTANT;
                case TOOL_CALL -> ROLE_ASSISTANT;
            };
        }

        @Override
        public MessageCount visit(final AgentGenericMessage genericMessage) {
            return genericMessage.accept(new AgentGenericMessageVisitor<>() {
                @Override
                public MessageCount visit(final GenericResource genericResource) {
                    return simple(roleOf(genericResource.getRole()),
                                  countString(encoder, genericResource.getSerializedJson()));
                }

                @Override
                public MessageCount visit(final GenericText genericText) {
                    return simple(roleOf(genericText.getRole()),
                                  countString(encoder, genericText.getText()));
                }
            });
        }

        @Override
        public MessageCount visit(final AgentRequest request) {
            return request.accept(new AgentRequestVisitor<>() {
                @Override
                public MessageCount visit(final SystemPrompt systemPrompt) {
                    return simple(ROLE_SYSTEM, countString(encoder, systemPrompt.getContent()));
                }

                @Override
                public MessageCount visit(final ToolCallResponse toolCallResponse) {
                    return withFormattingOverhead(ROLE_TOOL,
                                                  countString(encoder,
                                                              toolCallResponse.getToolCallId())
                                                          + countString(encoder,
                                                                        Objects.toString(
                                                                                         toolCallResponse
                                                                                                 .getResponse(),
                                                                                         null)));
                }

                @Override
                public MessageCount visit(final UserPrompt userPrompt) {
                    return simple(ROLE_USER,
                                  countUserMessage(encoder, userPrompt, config));
                }
            });
        }

        @Override
        public MessageCount visit(final AgentResponse response) {
            return response.accept(new AgentResponseVisitor<>() {
                @Override
                public MessageCount visit(final StructuredOutput structuredOutput) {
                    return simple(ROLE_ASSISTANT,
                                  countString(encoder, structuredOutput.getContent()));
                }

                @Override
                public MessageCount visit(final Text text) {
                    return simple(ROLE_ASSISTANT, countString(encoder, text.getContent()));
                }

                @Override
                public MessageCount visit(final ToolCall toolCall) {
                    var tokens = countString(encoder, toolCall.getToolCallId())
                            + countString(encoder, toolCall.getToolName());
                    if (!Strings.isNullOrEmpty(toolCall.getArguments())) {
                        tokens += config.getFormattingOverhead()
                                + countString(encoder, toolCall.getArguments());
                    }
                    return simple(ROLE_ASSISTANT, tokens);
                }
            });
        }

        private MessageCount simple(final String role, final int contentTokens) {
            return new MessageCount(role, countString(encoder, role), contentTokens);
        }

        private MessageCount withFormattingOverhead(final String role, final int contentTokens) {
            return new MessageCount(role,
                                    countString(encoder, role),
                                    config.getFormattingOverhead() + contentTokens);
        }
    }

    private final EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();

    private static int countString(final Encoding encoder, final String content) {
        return Strings.isNullOrEmpty(content) ? 0
                : encoder.encodeOrdinary(content)
                        .size();
    }

    /**
     * Counts tokens in user message content by content type. Image payloads are not tokenized as
     * text; each image contributes a fixed cost from the config. Audio and text are counted
     * normally.
     */
    private static int countUserMessage(final Encoding encoder,
                                        final UserPrompt userPrompt,
                                        final TokenCountingConfig tokenCountingConfig) {
        return switch (userPrompt.getContentType()) {
            case IMAGE_URL, IMAGE_DATA, FILE -> tokenCountingConfig.getImageTokenCost();
            case AUDIO -> countString(encoder, userPrompt.getContent());
            case TEXT -> countString(encoder, userPrompt.getContent());
        };
    }

    @Override
    public int estimateTokenCount(final List<AgentMessage> messages,
                                  final TokenCountingConfig tokenCountingConfig,
                                  final EncodingType encodingType) {
        final var currentEncodingType = Objects.requireNonNullElse(encodingType,
                                                                   EncodingType.CL100K_BASE);
        final var encoder = encodingRegistry.getEncoding(currentEncodingType);

        var totalTokens = 0;
        for (final var message : messages) {
            final var counted = message.accept(new Counter(encoder, tokenCountingConfig));
            totalTokens += tokenCountingConfig.getMessageOverHead() + counted.roleTokens();
            totalTokens += counted.contentTokens();
        }
        totalTokens += tokenCountingConfig.getAssistantPrimingOverhead();
        return totalTokens;
    }

    /**
     * Per-message counting result.
     */
    private record MessageCount(
            String role,
            int roleTokens,
            int contentTokens
    ) {
    }
}
