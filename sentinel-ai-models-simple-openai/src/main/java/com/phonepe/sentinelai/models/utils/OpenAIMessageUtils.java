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

package com.phonepe.sentinelai.models.utils;

import io.github.sashirestela.openai.common.function.FunctionCall;
import io.github.sashirestela.openai.common.tool.ToolChoiceOption;
import io.github.sashirestela.openai.common.tool.ToolType;
import io.github.sashirestela.openai.domain.chat.ChatMessage;

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
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.models.SimpleOpenAIModelOptions;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@UtilityClass
public class OpenAIMessageUtils {
    /**
     * Converts an individual Sentinel AgentMessage to OpenAI ChatMessage format.
     *
     * @param agentMessage Message to convert
     * @return OpenAI ChatMessage representation of the AgentMessage
     */
    public static ChatMessage convertIndividualMessageToOpenAIFormat(AgentMessage agentMessage) {
        return agentMessage.accept(new AgentMessageVisitor<>() {
            @Override
            public ChatMessage visit(AgentGenericMessage genericMessage) {
                return genericMessage.accept(
                                             new AgentGenericMessageVisitor<>() {
                                                 @Override
                                                 public ChatMessage visit(GenericResource genericResource) {
                                                     return switch (genericResource
                                                             .getRole()) {
                                                         case SYSTEM -> ChatMessage.SystemMessage
                                                                 .of(genericResource
                                                                         .getSerializedJson());
                                                         case USER -> ChatMessage.UserMessage
                                                                 .of(genericResource
                                                                         .getSerializedJson());
                                                         case ASSISTANT -> ChatMessage.AssistantMessage
                                                                 .of(genericResource
                                                                         .getSerializedJson());
                                                         case TOOL_CALL -> throw new UnsupportedOperationException(
                                                                                                                   "Tool calls are unsupported in this context");
                                                     };
                                                 }

                                                 @Override
                                                 public ChatMessage visit(GenericText genericText) {
                                                     return switch (genericText
                                                             .getRole()) {
                                                         case SYSTEM -> ChatMessage.SystemMessage
                                                                 .of(genericText
                                                                         .getText());
                                                         case USER -> ChatMessage.UserMessage
                                                                 .of(genericText
                                                                         .getText());
                                                         case ASSISTANT -> ChatMessage.AssistantMessage
                                                                 .of(genericText
                                                                         .getText());
                                                         case TOOL_CALL -> throw new UnsupportedOperationException(
                                                                                                                   "Tool calls are unsupported in this context");
                                                     };
                                                 }
                                             });
            }

            @Override
            public ChatMessage visit(AgentRequest request) {
                return request.accept(new AgentRequestVisitor<>() {
                    @Override
                    public ChatMessage visit(SystemPrompt systemPrompt) {
                        return ChatMessage.SystemMessage.of(systemPrompt
                                .getContent());
                    }

                    @Override
                    public ChatMessage visit(ToolCallResponse toolCallResponse) {
                        return ChatMessage.ToolMessage.of(toolCallResponse
                                .getResponse(),
                                                          toolCallResponse
                                                                  .getToolCallId());
                    }

                    @Override
                    public ChatMessage visit(UserPrompt userPrompt) {
                        return ChatMessage.UserMessage.of(userPrompt
                                .getContent());
                    }
                });
            }

            @Override
            public ChatMessage visit(AgentResponse response) {
                return response.accept(new AgentResponseVisitor<>() {
                    @Override
                    public ChatMessage visit(StructuredOutput structuredOutput) {
                        return ChatMessage.AssistantMessage.of(structuredOutput
                                .getContent());
                    }

                    @Override
                    public ChatMessage visit(Text text) {
                        return ChatMessage.AssistantMessage.of(text
                                .getContent());
                    }

                    @Override
                    public ChatMessage visit(ToolCall toolCall) {
                        return ChatMessage.AssistantMessage.of(List.of(
                                                                       new io.github.sashirestela.openai.common.tool.ToolCall(0,
                                                                                                                              toolCall.getToolCallId(),
                                                                                                                              ToolType.FUNCTION,
                                                                                                                              new FunctionCall(toolCall
                                                                                                                                      .getToolName(),
                                                                                                                                               toolCall.getArguments()))));
                    }
                });
            }
        });
    }

    /**
     * Converts sentinel messages to OpenAI message format.
     *
     * @param agentMessages List of sentinel messages to convert
     * @return List of OpenAI messages
     */
    public static List<ChatMessage> convertToOpenAIMessages(List<AgentMessage> agentMessages) {
        return AgentUtils.messagesAfterLastCompaction(agentMessages)
                .stream()
                .map(OpenAIMessageUtils::convertIndividualMessageToOpenAIFormat)
                .toList();

    }

    /**
     * Resolves the tool choice option for OpenAI models based on the output generation mode
     * and the tool choice specified in model options.
     * This is needed because some models like qwen and kimi (on vllm) are not calling tools properly
     * when output generation mode is set to TOOL_BASED and tool choice is set to REQUIRED.
     */
    public static ToolChoiceOption resolveToolChoice(OutputGenerationMode outputGenerationMode,
                                                     SimpleOpenAIModelOptions.ToolChoice toolChoice) {
        return switch (outputGenerationMode) {
            case TOOL_BASED -> switch (toolChoice) {
                case REQUIRED -> ToolChoiceOption.REQUIRED;
                case AUTO -> ToolChoiceOption.AUTO;
                case DEFAULT -> ToolChoiceOption.REQUIRED;
            };
            case STRUCTURED_OUTPUT -> switch (toolChoice) {
                case REQUIRED -> {
                    log.warn("Model is configured for STRUCTURED_OUTPUT generation mode, "
                            + "but tool choice is set to REQUIRED. This might lead to infinite tool-call loops");
                    yield ToolChoiceOption.REQUIRED;
                }
                case AUTO -> ToolChoiceOption.AUTO;
                case DEFAULT -> ToolChoiceOption.AUTO;
            };
        };
    }

}
