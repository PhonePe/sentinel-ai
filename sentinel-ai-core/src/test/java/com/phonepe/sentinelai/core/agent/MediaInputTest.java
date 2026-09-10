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

package com.phonepe.sentinelai.core.agent;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.MediaTypes.AudioFormat;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.MessageContentType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests for {@link MediaInput} factory methods.
 */
class MediaInputTest {

    @Test
    void audio() {
        final var content = "base64audiodata";
        final var mediaInput = MediaInput.audio(content, AudioFormat.MP3);

        assertEquals(MessageContentType.AUDIO, mediaInput.getContentType());
        assertEquals(content, mediaInput.getContent());
        assertEquals(AudioFormat.MP3, mediaInput.getAudioFormat());
        assertNull(mediaInput.getImageDetail());
        assertNull(mediaInput.getFileId());
        assertNull(mediaInput.getFileName());
    }

    @Test
    void fileContent() {
        final var content = "file content data";
        final var fileName = "report.txt";
        final var mediaInput = MediaInput.fileContent(content, fileName);

        assertEquals(MessageContentType.FILE, mediaInput.getContentType());
        assertEquals(content, mediaInput.getContent());
        assertEquals(fileName, mediaInput.getFileName());
        assertNull(mediaInput.getFileId());
        assertNull(mediaInput.getImageDetail());
        assertNull(mediaInput.getAudioFormat());
    }

    @Test
    void fileId() {
        final var fileId = "file-abc123";
        final var fileName = "document.pdf";
        final var mediaInput = MediaInput.fileId(fileId, fileName);

        assertEquals(MessageContentType.FILE, mediaInput.getContentType());
        assertEquals(fileId, mediaInput.getFileId());
        assertEquals(fileName, mediaInput.getFileName());
        assertNull(mediaInput.getContent());
        assertNull(mediaInput.getImageDetail());
        assertNull(mediaInput.getAudioFormat());
    }

    @Test
    void imageContent() {
        final var content = "iVBORw0KGgoAAAANS";
        final var mediaInput = MediaInput.imageContent(content, ImageDetail.HIGH);

        assertEquals(MessageContentType.IMAGE_DATA, mediaInput.getContentType());
        assertEquals(content, mediaInput.getContent());
        assertEquals(ImageDetail.HIGH, mediaInput.getImageDetail());
        assertNull(mediaInput.getAudioFormat());
        assertNull(mediaInput.getFileId());
        assertNull(mediaInput.getFileName());
    }

    @Test
    void imageUrl() {
        final var url = "https://example.com/image.png";
        final var mediaInput = MediaInput.imageUrl(url, ImageDetail.LOW);

        assertEquals(MessageContentType.IMAGE_URL, mediaInput.getContentType());
        assertEquals(url, mediaInput.getContent());
        assertEquals(ImageDetail.LOW, mediaInput.getImageDetail());
        assertNull(mediaInput.getAudioFormat());
        assertNull(mediaInput.getFileId());
        assertNull(mediaInput.getFileName());
    }
}
