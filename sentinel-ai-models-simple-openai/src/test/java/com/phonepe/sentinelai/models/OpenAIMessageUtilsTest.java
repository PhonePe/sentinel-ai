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

import io.github.sashirestela.openai.common.tool.ToolChoiceOption;
import io.github.sashirestela.openai.domain.chat.ChatMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.models.utils.OpenAIMessageUtils;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link OpenAIMessageUtils#resolveToolChoice(OutputGenerationMode, SimpleOpenAIModelOptions.ToolChoice)}
 * and the rendering of the per-user-message send time.
 */
class OpenAIMessageUtilsTest {

    static Stream<Arguments> resolveToolChoiceCases() {
        return Stream.of(
                         // TOOL_BASED mode
                         Arguments.of(OutputGenerationMode.TOOL_BASED,
                                      SimpleOpenAIModelOptions.ToolChoice.REQUIRED,
                                      ToolChoiceOption.REQUIRED),
                         Arguments.of(OutputGenerationMode.TOOL_BASED,
                                      SimpleOpenAIModelOptions.ToolChoice.AUTO,
                                      ToolChoiceOption.AUTO),
                         Arguments.of(OutputGenerationMode.TOOL_BASED,
                                      SimpleOpenAIModelOptions.ToolChoice.DEFAULT,
                                      ToolChoiceOption.REQUIRED),
                         // STRUCTURED_OUTPUT mode
                         Arguments.of(OutputGenerationMode.STRUCTURED_OUTPUT,
                                      SimpleOpenAIModelOptions.ToolChoice.REQUIRED,
                                      ToolChoiceOption.REQUIRED),
                         Arguments.of(OutputGenerationMode.STRUCTURED_OUTPUT,
                                      SimpleOpenAIModelOptions.ToolChoice.AUTO,
                                      ToolChoiceOption.AUTO),
                         Arguments.of(OutputGenerationMode.STRUCTURED_OUTPUT,
                                      SimpleOpenAIModelOptions.ToolChoice.DEFAULT,
                                      ToolChoiceOption.AUTO)
        );
    }

    @ParameterizedTest(name = "mode={0}, toolChoice={1} => {2}")
    @MethodSource("resolveToolChoiceCases")
    void testResolveToolChoice(OutputGenerationMode mode,
                               SimpleOpenAIModelOptions.ToolChoice toolChoice,
                               ToolChoiceOption expected) {
        assertEquals(expected, OpenAIMessageUtils.resolveToolChoice(mode, toolChoice));
    }

    @Test
    void userPromptRenderIsDeterministicForSameSentAt() {
        final var sentAt = LocalDateTime.of(2026, 7, 25, 10, 0, 0);
        final var first = new UserPrompt("s", "r", "<user_input/>", false, sentAt);
        final var second = new UserPrompt("s", "r", "<user_input/>", false, sentAt);

        final var c1 = String.valueOf(((ChatMessage.UserMessage) OpenAIMessageUtils
                .convertIndividualMessageToOpenAIFormat(first)).getContent());
        final var c2 = String.valueOf(((ChatMessage.UserMessage) OpenAIMessageUtils
                .convertIndividualMessageToOpenAIFormat(second)).getContent());

        assertEquals(c1, c2);
    }

    @Test
    void userPromptRendersAbsoluteUtcSendTime() {
        final var userPrompt = new UserPrompt("session-1",
                                              "run-1",
                                              "<user_input><data>hi</data></user_input>",
                                              false,
                                              LocalDateTime.of(2026, 7, 25, 10, 0, 0));

        final var converted = OpenAIMessageUtils.convertIndividualMessageToOpenAIFormat(userPrompt);

        final var userMessage = assertInstanceOf(ChatMessage.UserMessage.class, converted);
        final var content = String.valueOf(userMessage.getContent());
        assertTrue(content.startsWith("<sentAt>2026-07-25T10:00:00Z</sentAt>"),
                   "Expected absolute UTC ISO send-time prefix, got: " + content);
        assertTrue(content.contains("<user_input><data>hi</data></user_input>"),
                   "Original content must be preserved, got: " + content);
    }
}
