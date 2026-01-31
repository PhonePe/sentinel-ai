package com.phonepe.sentinelai.models;

import java.util.List;
import java.util.Objects;

import com.google.common.base.Strings;
import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.models.utils.OpenAIMessageUtils;

import io.github.sashirestela.openai.domain.chat.ChatMessage.AssistantMessage;
import io.github.sashirestela.openai.domain.chat.ChatMessage.DeveloperMessage;
import io.github.sashirestela.openai.domain.chat.ChatMessage.ResponseMessage;
import io.github.sashirestela.openai.domain.chat.ChatMessage.SystemMessage;
import io.github.sashirestela.openai.domain.chat.ChatMessage.ToolMessage;
import io.github.sashirestela.openai.domain.chat.ChatMessage.UserMessage;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
/**
 * Token counter for OpenAI completions models
 *
 * Uses the overheads and the encoding defined in the {@link TokenCountingConfig}
 */
public class OpenAICompletionsTokenCounter implements TokenCounter {

    private final TokenCountingConfig tokenCountingConfig;
    private final EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();

    /**
     * Estimate token counts in the given messages
     *
     * Counting heuristic:
     * - For each message, add a fixed overhead defined in the config
     * - For each message, add tokens for role
     * - For DeveloperMessage, add tokens for content and name (if present)
     * - For SystemMessage, add tokens for content and name (if present)
     * - For UserMessage, add tokens for content and name (if present)
     * - For AssistantMessage, add tokens for content, name (if present), refusal, and tool calls
     * - For ToolMessage, add tokens for tool call ID and content
     * - Finally, add a fixed overhead for assistant priming defined in the config
     */
    @Override
    public int estimateTokenCount(final List<AgentMessage> messages) {
        final var encoder = encodingRegistry.getEncoding(tokenCountingConfig.getEncoding());

        var totalTokens = 0;
        for (final var message : messages) {
        final var convertedMessage = OpenAIMessageUtils.convertIndividualMessageToOpenAIFormat(message);
            // Per message overhead is added irrespective of type of messages
            totalTokens += tokenCountingConfig.getMessageOverHead();

            // Add tokens for role. Added for all message types
            totalTokens += countString(encoder, convertedMessage.getRole().name());

            if(convertedMessage instanceof DeveloperMessage developerMessage) {
                totalTokens += countString(encoder, developerMessage.getContent());
                if(!Strings.isNullOrEmpty(developerMessage.getName())) {
                    totalTokens += tokenCountingConfig.getNameOverhead();
                    totalTokens += countString(encoder, developerMessage.getName());
                }
            }


            if(convertedMessage instanceof SystemMessage systemMessage) {
                totalTokens += countString(encoder, systemMessage.getContent());
                if(!Strings.isNullOrEmpty(systemMessage.getName())) {
                    totalTokens += tokenCountingConfig.getNameOverhead();
                    totalTokens += countString(encoder, systemMessage.getName());
                }
            }

            if(convertedMessage instanceof UserMessage userMessage) {
                totalTokens += countString(encoder, Objects.toString(userMessage.getContent()));
                if(!Strings.isNullOrEmpty(userMessage.getName())) {
                    totalTokens += tokenCountingConfig.getNameOverhead();
                    totalTokens += countString(encoder, userMessage.getName());
                }
            }

            if (convertedMessage instanceof AssistantMessage assistantMessage) {
                totalTokens += countString(encoder, Objects.toString(assistantMessage.getContent()));
                if(!Strings.isNullOrEmpty(assistantMessage.getName())) {
                    totalTokens += tokenCountingConfig.getNameOverhead();
                    totalTokens += countString(encoder, assistantMessage.getName());
                }
                totalTokens += countString(encoder, assistantMessage.getRefusal());
                final var toolCalls = Objects.requireNonNullElseGet(assistantMessage.getToolCalls(),
                        List::<io.github.sashirestela.openai.common.tool.ToolCall>of);

                for(final var toolCall : toolCalls) {
                    totalTokens += countString(encoder, toolCall.getId());
                    final var function = toolCall.getFunction();
                    totalTokens += countString(encoder, function.getName());
                    if(!Strings.isNullOrEmpty(function.getArguments())) {
                        totalTokens += tokenCountingConfig.getFormattingOverhead();
                        totalTokens += countString(encoder, function.getArguments());
                    }
                }
            }

            if(convertedMessage instanceof ToolMessage toolMessage) {
                totalTokens += tokenCountingConfig.getFormattingOverhead();
                totalTokens += countString(encoder, toolMessage.getToolCallId());
                totalTokens += countString(encoder, Objects.toString(toolMessage.getContent()));
            }

            // Response specific tokenCountingConfig
            if(convertedMessage instanceof ResponseMessage responseMessage) {
                totalTokens += countString(encoder, Objects.toString(responseMessage.getContent()));
            }
        }

        // Other message overheads
        totalTokens += tokenCountingConfig.getAssistantPrimingOverhead();
        return totalTokens;
    }

    /**
     * Count tokens in a Strings
     */
    private static int countString(final Encoding encoder, final String content) {
        return Strings.isNullOrEmpty(content)
            ? 0
            : encoder.encodeOrdinary(content).size();
    }
}
