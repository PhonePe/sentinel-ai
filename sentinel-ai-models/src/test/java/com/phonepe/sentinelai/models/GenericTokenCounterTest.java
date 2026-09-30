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

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.AudioFormat;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link GenericTokenCounter}. Ported from the old module's
 * {@code OpenAICompletionsTokenCounterTest}, adjusted to the neutral counter: role tokens come
 * from the neutral role name (lowercase) instead of the OpenAI DTO role enum.
 */
class GenericTokenCounterTest {

    private GenericTokenCounter tokenCounter;
    private Encoding encoder;

    @BeforeEach
    void setUp() {
        tokenCounter = new GenericTokenCounter();
        final EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();
        encoder = encodingRegistry.getEncoding(EncodingType.CL100K_BASE);
    }

    @Test
    void testEstimateTokenCountAssistantTextResponse() {
        final var content = "I am fine, thank you!";
        final var assistantResponse = new Text("s1", "r1", content, new ModelUsageStats(), 100);

        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + TokenCountingConfig.DEFAULT.getMessageOverHead()
                + countTokens("assistant")
                + countTokens(content);

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(assistantResponse),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountAudioPrompt() {
        final var audioData = "base64audiodata";
        final var sentAt = LocalDateTime.of(2026, 7, 25, 10, 0, 0);
        final var audioPrompt = UserPrompt.audio("s1",
                                                 "r1",
                                                 audioData,
                                                 AudioFormat.WAV,
                                                 sentAt);

        // Audio content is counted as text (the base64 data), not a fixed image cost.
        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + TokenCountingConfig.DEFAULT.getMessageOverHead()
                + countTokens("user")
                + countTokens(audioData);

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(audioPrompt),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountEmptyMessages() {
        assertEquals(TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead(),
                     tokenCounter.estimateTokenCount(List.of(),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountGenericText() {
        final var content = "Some generic text";
        final var genericText = new GenericText("s1",
                                                "r1",
                                                AgentGenericMessage.Role.USER,
                                                content);

        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + TokenCountingConfig.DEFAULT.getMessageOverHead()
                + countTokens("user")
                + countTokens(content);

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(genericText),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountImagePromptCountsFixedCostNotBase64() {
        final var base64Data = "iVBORw0KGgoAAAANSUhEUg".repeat(1000); // ~23KB of base64
        final var sentAt = LocalDateTime.of(2026, 7, 25, 10, 0, 0);
        final var imagePrompt = UserPrompt.imageData("s1",
                                                     "r1",
                                                     "data:image/png;base64," + base64Data,
                                                     ImageDetail.AUTO,
                                                     sentAt);

        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + TokenCountingConfig.DEFAULT.getMessageOverHead()
                + countTokens("user")
                + TokenCountingConfig.DEFAULT.getImageTokenCost();

        final var actual = tokenCounter.estimateTokenCount(List.of(imagePrompt),
                                                           TokenCountingConfig.DEFAULT,
                                                           EncodingType.CL100K_BASE);
        assertEquals(expected, actual);
        // The base64 payload must not be counted as text. 23K chars would be ~7K text tokens.
        assertTrue(actual < 1000, "Image tokens should be a fixed cost, not proportional to base64 length");
    }

    @Test
    void testEstimateTokenCountImagePromptWithCustomCost() {
        final var base64Data = "iVBORw0KGgoAAAANSUhEUg".repeat(1000);
        final var sentAt = LocalDateTime.of(2026, 7, 25, 10, 0, 0);
        final var imagePrompt = UserPrompt.imageData("s1",
                                                     "r1",
                                                     "data:image/png;base64," + base64Data,
                                                     ImageDetail.AUTO,
                                                     sentAt);
        final var config = TokenCountingConfig.DEFAULT.withImageTokenCost(1575);

        final var expected = config.getAssistantPrimingOverhead()
                + config.getMessageOverHead()
                + countTokens("user")
                + config.getImageTokenCost();

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(imagePrompt), config, EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountImageUrlPrompt() throws java.net.MalformedURLException {
        final var imageUrl = "https://example.com/image.png";
        final var sentAt = LocalDateTime.of(2026, 7, 25, 10, 0, 0);
        final var imagePrompt = UserPrompt.imageURL("s1",
                                                    "r1",
                                                    java.net.URI.create(imageUrl).toURL(),
                                                    ImageDetail.AUTO,
                                                    sentAt);

        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + TokenCountingConfig.DEFAULT.getMessageOverHead()
                + countTokens("user")
                + TokenCountingConfig.DEFAULT.getImageTokenCost();

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(imagePrompt),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountMultipleMessages() {
        final var systemPrompt = new SystemPrompt("s1", "r1", "System", false, null);
        final var sentAt = LocalDateTime.of(2026, 7, 25, 10, 0, 0);
        final var userPrompt = UserPrompt.text("s1", "r1", "User", sentAt);

        // The neutral counter counts user prompt content as-is; the <sentAt> prefix is a
        // wire-format concern owned by the protocol codec, not the counter.
        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + (TokenCountingConfig.DEFAULT.getMessageOverHead()
                        + countTokens("system")
                        + countTokens("System"))
                + (TokenCountingConfig.DEFAULT.getMessageOverHead()
                        + countTokens("user")
                        + countTokens("User"));

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(systemPrompt, userPrompt),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountStructuredOutput() {
        final var content = "{\"answer\": \"fine\"}";
        final var structuredOutput = new StructuredOutput("s1", "r1", content, new ModelUsageStats(), 100);

        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + TokenCountingConfig.DEFAULT.getMessageOverHead()
                + countTokens("assistant")
                + countTokens(content);

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(structuredOutput),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountSystemPrompt() {
        final var content = "You are a helpful assistant.";
        final var systemPrompt = new SystemPrompt("s1", "r1", content, false, null);

        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + TokenCountingConfig.DEFAULT.getMessageOverHead()
                + countTokens("system")
                + countTokens(content);

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(systemPrompt),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountToolCall() {
        final var toolName = "get_weather";
        final var arguments = "{\"location\": \"Bangalore\"}";
        final var toolCallId = "call_123";
        final var toolCall = new ToolCall("s1", "r1", toolCallId, toolName, arguments);

        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + TokenCountingConfig.DEFAULT.getMessageOverHead()
                + countTokens("assistant")
                + countTokens(toolCallId)
                + countTokens(toolName)
                + TokenCountingConfig.DEFAULT.getFormattingOverhead()
                + countTokens(arguments);

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(toolCall),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountToolCallResponse() {
        final var response = "Cloudy with a chance of meatballs";
        final var toolCallId = "call_123";
        final var toolName = "get_weather";
        final var toolCallResponse = new ToolCallResponse("s1",
                                                          "r1",
                                                          toolCallId,
                                                          toolName,
                                                          ErrorType.SUCCESS,
                                                          response,
                                                          LocalDateTime.now());

        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + TokenCountingConfig.DEFAULT.getMessageOverHead()
                + countTokens("tool")
                + TokenCountingConfig.DEFAULT.getFormattingOverhead()
                + countTokens(toolCallId)
                + countTokens(response);

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(toolCallResponse),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    @Test
    void testEstimateTokenCountUserPrompt() {
        final var content = "Hello, how are you?";
        final var sentAt = LocalDateTime.of(2026, 7, 25, 10, 0, 0);
        final var userPrompt = UserPrompt.text("s1", "r1", content, sentAt);

        final var expected = TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead()
                + TokenCountingConfig.DEFAULT.getMessageOverHead()
                + countTokens("user")
                + countTokens(content);

        assertEquals(expected,
                     tokenCounter.estimateTokenCount(List.of(userPrompt),
                                                     TokenCountingConfig.DEFAULT,
                                                     EncodingType.CL100K_BASE));
    }

    private int countTokens(final String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return encoder.encodeOrdinary(content).size();
    }
}
