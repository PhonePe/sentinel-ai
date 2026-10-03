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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
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

import lombok.SneakyThrows;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

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

    @SneakyThrows
    static Stream<CountingCase> countingCases() {
        final var sentAt = LocalDateTime.of(2026, 7, 25, 10, 0, 0);
        return Stream.of(
                         new CountingCase("assistant text response",
                                          List.of(new Text("s1",
                                                           "r1",
                                                           "I am fine, thank you!",
                                                           new ModelUsageStats(),
                                                           100)),
                                          self -> self.assistantOverhead() + self.messageOverhead("assistant")
                                                  + self.countTokens("I am fine, thank you!")),
                         new CountingCase("audio prompt counts base64 as text",
                                          List.of(UserPrompt.audio("s1",
                                                                   "r1",
                                                                   "base64audiodata",
                                                                   AudioFormat.WAV,
                                                                   sentAt)),
                                          self -> self.assistantOverhead() + self.messageOverhead("user")
                                                  + self.countTokens("base64audiodata")),
                         new CountingCase("empty messages only pay the priming overhead",
                                          List.of(),
                                          self -> self.assistantOverhead()),
                         new CountingCase("generic text",
                                          List.of(new GenericText("s1",
                                                                  "r1",
                                                                  AgentGenericMessage.Role.USER,
                                                                  "Some generic text")),
                                          self -> self.assistantOverhead() + self.messageOverhead("user")
                                                  + self.countTokens("Some generic text")),
                         new CountingCase("image prompt counts fixed cost not base64",
                                          List.of(UserPrompt.imageData("s1",
                                                                       "r1",
                                                                       "data:image/png;base64,"
                                                                               + "iVBORw0KGgoAAAANSUhEUg".repeat(1000),
                                                                       ImageDetail.AUTO,
                                                                       sentAt)),
                                          self -> self.assistantOverhead() + self.messageOverhead("user")
                                                  + TokenCountingConfig.DEFAULT.getImageTokenCost()),
                         new CountingCase("image url prompt",
                                          List.of(UserPrompt.imageURL("s1",
                                                                      "r1",
                                                                      java.net.URI.create(
                                                                                          "https://example.com/image.png")
                                                                              .toURL(),
                                                                      ImageDetail.AUTO,
                                                                      sentAt)),
                                          self -> self.assistantOverhead() + self.messageOverhead("user")
                                                  + TokenCountingConfig.DEFAULT.getImageTokenCost()),
                         new CountingCase("multiple messages",
                                          List.of(new SystemPrompt("s1", "r1", "System", false, null),
                                                  UserPrompt.text("s1", "r1", "User", sentAt)),
                                          self -> self.assistantOverhead() + self.messageOverhead("system")
                                                  + self.countTokens("System") + self.messageOverhead("user")
                                                  + self.countTokens("User")),
                         new CountingCase("structured output",
                                          List.of(new StructuredOutput("s1",
                                                                       "r1",
                                                                       "{\"answer\": \"fine\"}",
                                                                       new ModelUsageStats(),
                                                                       100)),
                                          self -> self.assistantOverhead() + self.messageOverhead("assistant")
                                                  + self.countTokens("{\"answer\": \"fine\"}")),
                         new CountingCase("system prompt",
                                          List.of(new SystemPrompt("s1",
                                                                   "r1",
                                                                   "You are a helpful assistant.",
                                                                   false,
                                                                   null)),
                                          self -> self.assistantOverhead() + self.messageOverhead("system")
                                                  + self.countTokens("You are a helpful assistant.")),
                         new CountingCase("tool call",
                                          List.of(new ToolCall("s1",
                                                               "r1",
                                                               "call_123",
                                                               "get_weather",
                                                               "{\"location\": \"Bangalore\"}")),
                                          self -> self.assistantOverhead() + TokenCountingConfig.DEFAULT
                                                  .getMessageOverHead()
                                                  + self.countTokens("assistant") + self.countTokens("call_123")
                                                  + self.countTokens("get_weather")
                                                  + TokenCountingConfig.DEFAULT.getFormattingOverhead()
                                                  + self.countTokens("{\"location\": \"Bangalore\"}")),
                         new CountingCase("tool call response",
                                          List.of(new ToolCallResponse("s1",
                                                                       "r1",
                                                                       "call_123",
                                                                       "get_weather",
                                                                       ErrorType.SUCCESS,
                                                                       "Cloudy with a chance of meatballs",
                                                                       sentAt)),
                                          self -> self.assistantOverhead() + TokenCountingConfig.DEFAULT
                                                  .getMessageOverHead()
                                                  + self.countTokens("tool")
                                                  + TokenCountingConfig.DEFAULT.getFormattingOverhead()
                                                  + self.countTokens("call_123")
                                                  + self.countTokens("Cloudy with a chance of meatballs")),
                         new CountingCase("user prompt",
                                          List.of(UserPrompt.text("s1", "r1", "Hello, how are you?", sentAt)),
                                          self -> self.assistantOverhead() + self.messageOverhead("user")
                                                  + self.countTokens("Hello, how are you?")));
    }

    @BeforeEach
    void setUp() {
        tokenCounter = new GenericTokenCounter();
        final EncodingRegistry encodingRegistry = Encodings.newDefaultEncodingRegistry();
        encoder = encodingRegistry.getEncoding(EncodingType.CL100K_BASE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("countingCases")
    void testEstimateTokenCount(final CountingCase countingCase) {
        final var expected = countingCase.expected().applyAsInt(this);
        final var actual = tokenCounter.estimateTokenCount(countingCase.messages(),
                                                           TokenCountingConfig.DEFAULT,
                                                           EncodingType.CL100K_BASE);
        assertEquals(expected, actual, countingCase.name());
    }

    @Test
    void testImagePromptWithCustomCostConfig() {
        final var base64Data = "iVBORw0KGgoAAAANSUhEUg".repeat(1000);
        final var imagePrompt = UserPrompt.imageData("s1",
                                                     "r1",
                                                     "data:image/png;base64," + base64Data,
                                                     ImageDetail.AUTO,
                                                     LocalDateTime.of(2026, 7, 25, 10, 0, 0));
        final var config = TokenCountingConfig.DEFAULT.withImageTokenCost(1575);

        final var expected = config.getAssistantPrimingOverhead()
                + config.getMessageOverHead()
                + countTokens("user")
                + config.getImageTokenCost();

        assertEquals(expected, tokenCounter.estimateTokenCount(List.of(imagePrompt), config, EncodingType.CL100K_BASE));
    }

    @Test
    void testImageTokensAreAFixedCostNotProportionalToBase64Length() {
        final var base64Data = "iVBORw0KGgoAAAANSUhEUg".repeat(1000); // ~23KB of base64
        final var imagePrompt = UserPrompt.imageData("s1",
                                                     "r1",
                                                     "data:image/png;base64," + base64Data,
                                                     ImageDetail.AUTO,
                                                     LocalDateTime.of(2026, 7, 25, 10, 0, 0));

        final var actual = tokenCounter.estimateTokenCount(List.of(imagePrompt),
                                                           TokenCountingConfig.DEFAULT,
                                                           EncodingType.CL100K_BASE);
        // The base64 payload must not be counted as text. 23K chars would be ~7K text tokens.
        assertTrue(actual < 1000, "Image tokens should be a fixed cost, not proportional to base64 length");
    }

    private record CountingCase(
            String name,
            List<AgentMessage> messages,
            ToIntFunction<GenericTokenCounterTest> expected
    ) {
    }

    private int assistantOverhead() {
        return TokenCountingConfig.DEFAULT.getAssistantPrimingOverhead();
    }

    private int countTokens(final String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return encoder.encodeOrdinary(content).size();
    }

    private int messageOverhead(final String role) {
        return TokenCountingConfig.DEFAULT.getMessageOverHead() + countTokens(role);
    }
}
