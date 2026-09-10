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
import io.github.sashirestela.openai.common.tool.ToolType;
import io.github.sashirestela.openai.domain.chat.ChatMessage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericResource;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.models.utils.OpenAIMessageUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests message conversion and tool choice resolution in {@link OpenAIMessageUtils}.
 */
class OpenAIMessageUtilsTest {

    private static final String SESSION_ID = "session-1";
    private static final String RUN_ID = "run-1";
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 7, 25, 10, 0, 0);

    static Stream<Arguments> messages() {
        return Stream.of(Arguments.of(SystemPrompt.builder().sessionId(SESSION_ID).content("rules").build(),
                                      ChatMessage.SystemMessage.class,
                                      "rules"),
                         Arguments.of(new UserPrompt(SESSION_ID, RUN_ID, "hi", false, SENT_AT),
                                      ChatMessage.UserMessage.class,
                                      "<sentAt>2026-07-25T10:00:00Z</sentAt>\nhi"),
                         Arguments.of(ToolCallResponse.builder()
                                 .sessionId(SESSION_ID)
                                 .toolCallId("call-1")
                                 .toolName("weather")
                                 .response("{}")
                                 .build(), ChatMessage.ToolMessage.class, "{}"),
                         Arguments.of(new Text(SESSION_ID, RUN_ID, "hello", new ModelUsageStats(), 1L),
                                      ChatMessage.AssistantMessage.class,
                                      "hello"),
                         Arguments.of(StructuredOutput.builder()
                                 .sessionId(SESSION_ID)
                                 .content("{\"a\":1}")
                                 .stats(new ModelUsageStats())
                                 .build(), ChatMessage.AssistantMessage.class, "{\"a\":1}"),
                         Arguments.of(genericText(AgentGenericMessage.Role.SYSTEM),
                                      ChatMessage.SystemMessage.class,
                                      "some text"),
                         Arguments.of(genericText(AgentGenericMessage.Role.USER),
                                      ChatMessage.UserMessage.class,
                                      "some text"),
                         Arguments.of(genericText(AgentGenericMessage.Role.ASSISTANT),
                                      ChatMessage.AssistantMessage.class,
                                      "some text"),
                         Arguments.of(genericResource(AgentGenericMessage.Role.SYSTEM),
                                      ChatMessage.SystemMessage.class,
                                      "{\"k\":\"v\"}"),
                         Arguments.of(genericResource(AgentGenericMessage.Role.USER),
                                      ChatMessage.UserMessage.class,
                                      "{\"k\":\"v\"}"),
                         Arguments.of(genericResource(AgentGenericMessage.Role.ASSISTANT),
                                      ChatMessage.AssistantMessage.class,
                                      "{\"k\":\"v\"}"));
    }

    static Stream<Arguments> toolChoices() {
        return Stream.of(Arguments.of(OutputGenerationMode.TOOL_BASED,
                                      SimpleOpenAIModelOptions.ToolChoice.REQUIRED,
                                      ToolChoiceOption.REQUIRED),
                         Arguments.of(OutputGenerationMode.TOOL_BASED,
                                      SimpleOpenAIModelOptions.ToolChoice.AUTO,
                                      ToolChoiceOption.AUTO),
                         Arguments.of(OutputGenerationMode.TOOL_BASED,
                                      SimpleOpenAIModelOptions.ToolChoice.DEFAULT,
                                      ToolChoiceOption.REQUIRED),
                         Arguments.of(OutputGenerationMode.STRUCTURED_OUTPUT,
                                      SimpleOpenAIModelOptions.ToolChoice.REQUIRED,
                                      ToolChoiceOption.REQUIRED),
                         Arguments.of(OutputGenerationMode.STRUCTURED_OUTPUT,
                                      SimpleOpenAIModelOptions.ToolChoice.AUTO,
                                      ToolChoiceOption.AUTO),
                         Arguments.of(OutputGenerationMode.STRUCTURED_OUTPUT,
                                      SimpleOpenAIModelOptions.ToolChoice.DEFAULT,
                                      ToolChoiceOption.AUTO));
    }

    private static String content(ChatMessage message) {
        if (message instanceof ChatMessage.SystemMessage systemMessage) {
            return systemMessage.getContent();
        }
        if (message instanceof ChatMessage.UserMessage userMessage) {
            return Objects.toString(userMessage.getContent());
        }
        if (message instanceof ChatMessage.ToolMessage toolMessage) {
            return toolMessage.getContent();
        }
        return Objects.toString(((ChatMessage.AssistantMessage) message).getContent());
    }

    private static GenericResource genericResource(AgentGenericMessage.Role role) {
        return new GenericResource(SESSION_ID,
                                   RUN_ID,
                                   role,
                                   GenericResource.ResourceType.TEXT,
                                   "file:///tmp/a.txt",
                                   "text/plain",
                                   "some text",
                                   "{\"k\":\"v\"}");
    }

    private static GenericText genericText(AgentGenericMessage.Role role) {
        return new GenericText(SESSION_ID, RUN_ID, role, "some text");
    }

    @ParameterizedTest(name = "{0} => {1}")
    @MethodSource("messages")
    void convert(AgentMessage message, Class<? extends ChatMessage> expectedType, String expectedContent) {
        final var converted = OpenAIMessageUtils.convertIndividualMessageToOpenAIFormat(message);

        assertInstanceOf(expectedType, converted);
        assertEquals(expectedContent, content(converted));
    }

    @Test
    void convertList() {
        final List<AgentMessage> messages = List.of(SystemPrompt.builder()
                .sessionId(SESSION_ID)
                .content("rules")
                .build(),
                                                    new UserPrompt(SESSION_ID, RUN_ID, "hi", false, SENT_AT),
                                                    new Text(SESSION_ID, RUN_ID, "hello", new ModelUsageStats(), 1L));

        final var converted = OpenAIMessageUtils.convertToOpenAIMessages(messages);

        assertEquals(3, converted.size());
        assertInstanceOf(ChatMessage.SystemMessage.class, converted.get(0));
        assertInstanceOf(ChatMessage.UserMessage.class, converted.get(1));
        assertInstanceOf(ChatMessage.AssistantMessage.class, converted.get(2));
    }

    @Test
    void convertListSkipsCompacted() {
        final List<AgentMessage> messages = List.of(SystemPrompt.builder()
                .sessionId(SESSION_ID)
                .content("rules")
                .build(),
                                                    new UserPrompt(SESSION_ID, RUN_ID, "old", false, SENT_AT),
                                                    new Text(SESSION_ID, RUN_ID, "answer", new ModelUsageStats(), 1L),
                                                    new UserPrompt(SESSION_ID, RUN_ID, "summary", true, SENT_AT),
                                                    new UserPrompt(SESSION_ID, RUN_ID, "new", false, SENT_AT));

        final var converted = OpenAIMessageUtils.convertToOpenAIMessages(messages);

        assertEquals(3, converted.size());
        assertInstanceOf(ChatMessage.SystemMessage.class, converted.get(0));
        assertTrue(content(converted.get(1)).endsWith("summary"));
        assertTrue(content(converted.get(2)).endsWith("new"));
    }

    @Test
    void convertNullList() {
        assertTrue(OpenAIMessageUtils.convertToOpenAIMessages(null).isEmpty());
    }

    @Test
    void convertToolCall() {
        final var toolCall = ToolCall.builder()
                .sessionId(SESSION_ID)
                .toolCallId("call-7")
                .toolName("weather")
                .arguments("{\"city\":\"Bangalore\"}")
                .build();

        final var message = assertInstanceOf(ChatMessage.AssistantMessage.class,
                                             OpenAIMessageUtils.convertIndividualMessageToOpenAIFormat(toolCall));

        final var converted = message.getToolCalls().get(0);
        assertEquals(1, message.getToolCalls().size());
        assertEquals("call-7", converted.getId());
        assertEquals(ToolType.FUNCTION, converted.getType());
        assertEquals("weather", converted.getFunction().getName());
        assertEquals("{\"city\":\"Bangalore\"}", converted.getFunction().getArguments());
    }

    @Test
    void genericToolCallRoleFails() {
        final var text = genericText(AgentGenericMessage.Role.TOOL_CALL);
        final var resource = genericResource(AgentGenericMessage.Role.TOOL_CALL);

        assertThrows(UnsupportedOperationException.class,
                     () -> OpenAIMessageUtils.convertIndividualMessageToOpenAIFormat(text));
        assertThrows(UnsupportedOperationException.class,
                     () -> OpenAIMessageUtils.convertIndividualMessageToOpenAIFormat(resource));
    }

    @ParameterizedTest(name = "{0} + {1} => {2}")
    @MethodSource("toolChoices")
    void toolChoice(OutputGenerationMode mode,
                    SimpleOpenAIModelOptions.ToolChoice toolChoice,
                    ToolChoiceOption expected) {
        assertEquals(expected, OpenAIMessageUtils.resolveToolChoice(mode, toolChoice));
    }
}
