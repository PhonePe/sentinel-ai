package com.phonepe.sentinelai.models.utils;

import com.phonepe.sentinelai.core.agentmessages.*;
import com.phonepe.sentinelai.core.agentmessages.requests.*;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import io.github.sashirestela.openai.common.function.FunctionCall;
import io.github.sashirestela.openai.common.tool.ToolType;
import io.github.sashirestela.openai.domain.chat.ChatMessage;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Objects;

@UtilityClass
public class OpenAIMessageUtils {
    /**
     * Converts sentinel messages to OpenAI message format.
     *
     * @param agentMessages List of sentinel messages to convert
     * @return List of OpenAI messages
     */
    public static List<ChatMessage> convertToOpenAIMessages(List<AgentMessage> agentMessages) {
        return Objects.requireNonNullElseGet(agentMessages, List::<AgentMessage>of)
                .stream()
                .map(OpenAIMessageUtils::convertIndividualMessageToOpenAIFormat)
                .toList();

    }

    /**
     * Converts an individual Sentinel AgentMessage to OpenAI ChatMessage format.
     *
     * @param agentMessage Message to convert
     * @return OpenAI ChatMessage representation of the AgentMessage
     */
    public static ChatMessage convertIndividualMessageToOpenAIFormat(AgentMessage agentMessage) {
        return agentMessage.accept(new AgentMessageVisitor<>() {
            @Override
            public ChatMessage visit(AgentRequest request) {
                return request.accept(new AgentRequestVisitor<>() {
                    @Override
                    public ChatMessage visit(SystemPrompt systemPrompt) {
                        return ChatMessage.SystemMessage.of(systemPrompt.getContent());
                    }

                    @Override
                    public ChatMessage visit(UserPrompt userPrompt) {
                        return ChatMessage.UserMessage.of(userPrompt.getContent());
                    }

                    @Override
                    public ChatMessage visit(ToolCallResponse toolCallResponse) {
                        return ChatMessage.ToolMessage.of(toolCallResponse.getResponse(),
                                toolCallResponse.getToolCallId());
                    }
                });
            }

            @Override
            public ChatMessage visit(AgentResponse response) {
                return response.accept(new AgentResponseVisitor<>() {
                    @Override
                    public ChatMessage visit(Text text) {
                        return ChatMessage.AssistantMessage.of(text.getContent());
                    }

                    @Override
                    public ChatMessage visit(StructuredOutput structuredOutput) {
                        return ChatMessage.AssistantMessage.of(structuredOutput.getContent());
                    }

                    @Override
                    public ChatMessage visit(ToolCall toolCall) {
                        return ChatMessage.AssistantMessage.of(List.of(new io.github.sashirestela.openai.common.tool.ToolCall(
                                0,
                                toolCall.getToolCallId(),
                                ToolType.FUNCTION,
                                new FunctionCall(toolCall.getToolName(), toolCall.getArguments()))));
                    }
                });
            }

            @Override
            public ChatMessage visit(AgentGenericMessage genericMessage) {
                return genericMessage.accept(new AgentGenericMessageVisitor<>() {
                    @Override
                    public ChatMessage visit(GenericText genericText) {
                        return switch (genericText.getRole()) {
                            case SYSTEM -> ChatMessage.SystemMessage.of(genericText.getText());
                            case USER -> ChatMessage.UserMessage.of(genericText.getText());
                            case ASSISTANT -> ChatMessage.AssistantMessage.of(genericText.getText());
                            case TOOL_CALL -> throw new UnsupportedOperationException(
                                    "Tool calls are unsupported in this context");
                        };
                    }

                    @Override
                    public ChatMessage visit(GenericResource genericResource) {
                        return switch (genericResource.getRole()) {
                            case SYSTEM -> ChatMessage.SystemMessage.of(genericResource.getSerializedJson());
                            case USER -> ChatMessage.UserMessage.of(genericResource.getSerializedJson());
                            case ASSISTANT -> ChatMessage.AssistantMessage.of(genericResource.getSerializedJson());
                            case TOOL_CALL -> throw new UnsupportedOperationException(
                                    "Tool calls are unsupported in this context");
                        };
                    }
                });
            }
        });
    }
}
