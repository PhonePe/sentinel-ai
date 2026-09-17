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

package com.phonepe.sentinelai.core.agentmessages.requests;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.MediaTypes.AudioFormat;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.MessageContentType;
import com.phonepe.sentinelai.core.utils.JsonUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Locks in the cache-stability guarantee for the per-user-message send time: a {@link UserPrompt}
 * carrying a {@code sentAt} must round-trip through the session store (Jackson) byte-identically, so
 * a replayed history turn produces exactly the same prompt prefix and does not bust the LLM prompt
 * cache.
 */
class UserPromptSerdeTest {

    private final ObjectMapper mapper = JsonUtils.createMapper();

    @Test
    void audioRoundTrip() throws Exception {
        final var original = UserPrompt.audio("session-1",
                                              "run-1",
                                              "base64audiodata",
                                              AudioFormat.MP3,
                                              LocalDateTime.of(2026, 7, 25, 10, 0, 0));
        final var json = mapper.writeValueAsString(original);
        final var revived = mapper.readValue(json, UserPrompt.class);

        assertEquals(MessageContentType.AUDIO, revived.getContentType());
        assertEquals(original.getContent(), revived.getContent());
        assertEquals(original.getAudioFormat(), revived.getAudioFormat());
        assertEquals(json, mapper.writeValueAsString(revived));
    }

    @Test
    void fileRoundTrip() throws Exception {
        final var original = UserPrompt.file("session-1",
                                             "run-1",
                                             "file-content-here",
                                             "file-123",
                                             "report.pdf",
                                             LocalDateTime.of(2026, 7, 25, 10, 0, 0));
        final var json = mapper.writeValueAsString(original);
        final var revived = mapper.readValue(json, UserPrompt.class);

        assertEquals(MessageContentType.FILE, revived.getContentType());
        assertEquals(original.getContent(), revived.getContent());
        assertEquals(original.getFileId(), revived.getFileId());
        assertEquals(original.getFileName(), revived.getFileName());
        assertEquals(json, mapper.writeValueAsString(revived));
    }

    @Test
    void imageDataRoundTrip() throws Exception {
        final var original = UserPrompt.imageData("session-1",
                                                  "run-1",
                                                  "data:image/jpeg;base64,iVBORw0KGgoAAAANS",
                                                  ImageDetail.HIGH,
                                                  LocalDateTime.of(2026, 7, 25, 10, 0, 0));
        final var json = mapper.writeValueAsString(original);
        final var revived = mapper.readValue(json, UserPrompt.class);

        assertEquals(MessageContentType.IMAGE_DATA, revived.getContentType());
        assertEquals(original.getContent(), revived.getContent());
        assertEquals(original.getImageDetail(), revived.getImageDetail());
        assertEquals(json, mapper.writeValueAsString(revived));
    }

    @Test
    void repeatedSerializationIsStable() throws Exception {
        final var prompt = UserPrompt.text("session-1",
                                           "run-1",
                                           "<user_input><data>hi</data></user_input>",
                                           LocalDateTime.of(2026, 7, 25, 10, 0, 0));

        assertEquals(mapper.writeValueAsString(prompt), mapper.writeValueAsString(prompt));
    }

    @Test
    void sentAtSurvivesRoundTripUnchanged() throws Exception {
        final var original = UserPrompt.text("session-1",
                                             "run-1",
                                             "<user_input><data>hello</data></user_input>",
                                             LocalDateTime.of(2026, 7, 25, 10, 0, 0));
        final var json = mapper.writeValueAsString(original);
        final var revived = mapper.readValue(json, UserPrompt.class);

        assertEquals(original.getSentAt(), revived.getSentAt());
        assertEquals(original.getContent(), revived.getContent());

        // Serializing the revived message must be byte-identical to the first serialization.
        assertEquals(json, mapper.writeValueAsString(revived));
    }

    @Test
    @SuppressWarnings("java:S5778")
    void testImageFilePrefixAssertion() {
        assertThrows(IllegalArgumentException.class,
                     () -> UserPrompt.imageData("session-1",
                                                "run-1",
                                                "iVBORw0KGgoAAAANS",
                                                ImageDetail.HIGH,
                                                LocalDateTime.of(2026, 7, 25, 10, 0, 0)));
    }
}
