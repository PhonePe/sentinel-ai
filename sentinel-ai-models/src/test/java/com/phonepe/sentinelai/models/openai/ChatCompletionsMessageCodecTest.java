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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.phonepe.sentinelai.core.agentmessages.AgentGenericMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericResource;
import com.phonepe.sentinelai.core.agentmessages.requests.GenericText;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.StructuredOutput;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ChatCompletionsMessageCodec}. Port of the old {@code OpenAIMessageUtilsTest}: the
 * same message scenarios, asserted on the Chat Completions message JSON instead of vendor DTOs.
 */
class ChatCompletionsMessageCodecTest {

    private static final String SESSION_ID = "session-1";
    private static final String RUN_ID = "run-1";
    private static final LocalDateTime SENT_AT = LocalDateTime.of(2026, 7, 25, 10, 0, 0);

    private final ObjectMapper mapper = JsonUtils.createMapper();
    private final ChatCompletionsMessageCodec codec = new ChatCompletionsMessageCodec(mapper);

    static Stream<Arguments> imageDetails() {
        return Stream.of(Arguments.of(ImageDetail.AUTO, "auto"),
                         Arguments.of(ImageDetail.LOW, "low"),
                         Arguments.of(ImageDetail.HIGH, "high"));
    }

    static Stream<Arguments> messages() {
        return Stream.of(Arguments.of(SystemPrompt.builder().sessionId(SESSION_ID).content("rules").build(),
                                      ChatCompletionsFields.ROLE_SYSTEM,
                                      "rules"),
                         Arguments.of(UserPrompt.text(SESSION_ID, RUN_ID, "hi", SENT_AT),
                                      ChatCompletionsFields.ROLE_USER,
                                      "<sentAt>2026-07-25T10:00:00Z</sentAt>\nhi"),
                         Arguments.of(ToolCallResponse.builder()
                                 .sessionId(SESSION_ID)
                                 .toolCallId("call-1")
                                 .toolName("weather")
                                 .response("{}")
                                 .build(),
                                      ChatCompletionsFields.ROLE_TOOL,
                                      "{}"),
                         Arguments.of(new Text(SESSION_ID, RUN_ID, "hello", new ModelUsageStats(), 1L),
                                      ChatCompletionsFields.ROLE_ASSISTANT,
                                      "hello"),
                         Arguments.of(StructuredOutput.builder()
                                 .sessionId(SESSION_ID)
                                 .content("{\"a\":1}")
                                 .stats(new ModelUsageStats())
                                 .build(),
                                      ChatCompletionsFields.ROLE_ASSISTANT,
                                      "{\"a\":1}"),
                         Arguments.of(genericText(AgentGenericMessage.Role.SYSTEM),
                                      ChatCompletionsFields.ROLE_SYSTEM,
                                      "some text"),
                         Arguments.of(genericText(AgentGenericMessage.Role.USER),
                                      ChatCompletionsFields.ROLE_USER,
                                      "some text"),
                         Arguments.of(genericText(AgentGenericMessage.Role.ASSISTANT),
                                      ChatCompletionsFields.ROLE_ASSISTANT,
                                      "some text"),
                         Arguments.of(genericResource(AgentGenericMessage.Role.SYSTEM),
                                      ChatCompletionsFields.ROLE_SYSTEM,
                                      "{\"k\":\"v\"}"),
                         Arguments.of(genericResource(AgentGenericMessage.Role.USER),
                                      ChatCompletionsFields.ROLE_USER,
                                      "{\"k\":\"v\"}"),
                         Arguments.of(genericResource(AgentGenericMessage.Role.ASSISTANT),
                                      ChatCompletionsFields.ROLE_ASSISTANT,
                                      "{\"k\":\"v\"}"));
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

    @ParameterizedTest(name = "{0} => role {1}")
    @MethodSource("messages")
    void convert(AgentMessage message, String expectedRole, String expectedContent) {
        final var converted = codec.translate(message);

        assertEquals(expectedRole, converted.get(ChatCompletionsFields.ROLE).asText());
        final var content = converted.get(ChatCompletionsFields.CONTENT);
        assertTrue(content != null && !content.isNull());
        assertTrue(content.asText().equals(expectedContent) || content.asText().endsWith(expectedContent));
    }

    @Test
    void convertAudioPrompt() {
        final var audioData = "base64audiodata";
        final var userPrompt = UserPrompt.audio(SESSION_ID,
                                                RUN_ID,
                                                audioData,
                                                com.phonepe.sentinelai.core.agentmessages.MediaTypes.AudioFormat.MP3,
                                                SENT_AT);

        final var converted = codec.translate(userPrompt);

        assertEquals(ChatCompletionsFields.ROLE_USER, converted.get(ChatCompletionsFields.ROLE).asText());
        final var contentParts = converted.get(ChatCompletionsFields.CONTENT);
        assertTrue(contentParts.isArray());
        assertEquals(1, contentParts.size());
        final var part = contentParts.get(0);
        assertEquals(ChatCompletionsFields.INPUT_AUDIO, part.get(ChatCompletionsFields.TYPE).asText());
        final var inputAudio = part.get(ChatCompletionsFields.INPUT_AUDIO);
        assertEquals(audioData, inputAudio.get(ChatCompletionsFields.DATA).asText());
        assertEquals("mp3", inputAudio.get(ChatCompletionsFields.FORMAT).asText());
    }

    @Test
    void convertFilePromptThrowsException() {
        final var userPrompt = UserPrompt.file(SESSION_ID,
                                               RUN_ID,
                                               "file content",
                                               "file-123",
                                               "report.txt",
                                               SENT_AT);

        assertThrows(UnsupportedOperationException.class, () -> codec.translate(userPrompt));
    }

    @Test
    void convertImageDataPrompt() {
        final var base64Data = "iVBORw0KGgoAAAANS";
        final var userPrompt = UserPrompt.imageData(SESSION_ID,
                                                    RUN_ID,
                                                    "data:image/png;base64," + base64Data,
                                                    ImageDetail.AUTO,
                                                    SENT_AT);

        final var converted = codec.translate(userPrompt);

        assertEquals(ChatCompletionsFields.ROLE_USER, converted.get(ChatCompletionsFields.ROLE).asText());
        final var contentParts = converted.get(ChatCompletionsFields.CONTENT);
        assertTrue(contentParts.isArray());
        assertEquals(1, contentParts.size());
        final var part = contentParts.get(0);
        assertEquals(ChatCompletionsFields.IMAGE_URL, part.get(ChatCompletionsFields.TYPE).asText());
        final var imageUrl = part.get(ChatCompletionsFields.IMAGE_URL);
        assertEquals("data:image/png;base64," + base64Data,
                     imageUrl.get(ChatCompletionsFields.URL).asText());
        assertEquals("auto", imageUrl.get(ChatCompletionsFields.DETAIL).asText());
    }

    @ParameterizedTest(name = "detail={0}")
    @MethodSource("imageDetails")
    void convertImageDataWithDifferentDetailLevels(ImageDetail detail, String expectedWireDetail) {
        final var userPrompt = UserPrompt.imageData(SESSION_ID,
                                                    RUN_ID,
                                                    "data:image/png;base64,base64data",
                                                    detail,
                                                    SENT_AT);

        final var converted = codec.translate(userPrompt);

        final var part = converted.get(ChatCompletionsFields.CONTENT).get(0);
        assertEquals(expectedWireDetail,
                     part.get(ChatCompletionsFields.IMAGE_URL).get(ChatCompletionsFields.DETAIL).asText());
    }

    @Test
    void convertImageUrlPrompt() throws java.net.MalformedURLException {
        final var userPrompt = UserPrompt.imageURL(SESSION_ID,
                                                   RUN_ID,
                                                   java.net.URI.create("https://example.com/image.png").toURL(),
                                                   ImageDetail.HIGH,
                                                   SENT_AT);

        final var converted = codec.translate(userPrompt);

        final var part = converted.get(ChatCompletionsFields.CONTENT).get(0);
        assertEquals("https://example.com/image.png",
                     part.get(ChatCompletionsFields.IMAGE_URL).get(ChatCompletionsFields.URL).asText());
        assertEquals("high", part.get(ChatCompletionsFields.IMAGE_URL).get(ChatCompletionsFields.DETAIL).asText());
    }

    @Test
    void convertTextWithNullSentAtDefaultsToNow() {
        final var userPrompt = UserPrompt.text(SESSION_ID, RUN_ID, "hi", null);

        final var converted = codec.translate(userPrompt);

        assertEquals(ChatCompletionsFields.ROLE_USER, converted.get(ChatCompletionsFields.ROLE).asText());
        final var content = converted.get(ChatCompletionsFields.CONTENT).asText();
        assertTrue(content.startsWith("<sentAt>"));
        assertTrue(content.endsWith("</sentAt>\nhi"));
    }

    @Test
    void convertToolCall() {
        final var toolCall = ToolCall.builder()
                .sessionId(SESSION_ID)
                .toolCallId("call-7")
                .toolName("weather")
                .arguments("{\"city\":\"Bangalore\"}")
                .build();

        final var converted = codec.translate(toolCall);

        assertEquals(ChatCompletionsFields.ROLE_ASSISTANT, converted.get(ChatCompletionsFields.ROLE).asText());
        final var calls = converted.get(ChatCompletionsFields.TOOL_CALLS);
        assertEquals(1, calls.size());
        final var call = calls.get(0);
        assertEquals("call-7", call.get(ChatCompletionsFields.ID).asText());
        assertEquals(ChatCompletionsFields.TYPE_FUNCTION, call.get(ChatCompletionsFields.TYPE).asText());
        assertEquals("weather",
                     call.get(ChatCompletionsFields.FUNCTION).get(ChatCompletionsFields.NAME).asText());
        assertEquals("{\"city\":\"Bangalore\"}",
                     call.get(ChatCompletionsFields.FUNCTION).get(ChatCompletionsFields.ARGUMENTS).asText());
    }

    @Test
    void convertToolCallResponse() {
        final var toolCallResponse = ToolCallResponse.builder()
                .sessionId(SESSION_ID)
                .toolCallId("call-1")
                .toolName("weather")
                .response("{\"temp\":31}")
                .build();

        final var converted = codec.translate(toolCallResponse);

        assertEquals(ChatCompletionsFields.ROLE_TOOL, converted.get(ChatCompletionsFields.ROLE).asText());
        assertEquals("call-1", converted.get(ChatCompletionsFields.TOOL_CALL_ID).asText());
        assertEquals("{\"temp\":31}", converted.get(ChatCompletionsFields.CONTENT).asText());
    }

    @Test
    void genericToolCallRoleFails() {
        final var text = genericText(AgentGenericMessage.Role.TOOL_CALL);
        final var resource = genericResource(AgentGenericMessage.Role.TOOL_CALL);

        assertThrows(UnsupportedOperationException.class, () -> codec.translate(text));
        assertThrows(UnsupportedOperationException.class, () -> codec.translate(resource));
    }
}
