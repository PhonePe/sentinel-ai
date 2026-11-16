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

package com.phonepe.sentinelai.core.hooks;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.ImageDetail;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.MessageContentType;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ImageLimitPreProcessor}.
 */
class ImageLimitPreProcessorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2025, 1, 1, 0, 0);

    private static UserPrompt imagePrompt(final String content) {
        return UserPrompt.imageData("session-1", "run-1", content, ImageDetail.AUTO, NOW);
    }

    private static AgentMessage systemPrompt() {
        return new SystemPrompt("session-1", "run-1", "system prompt", false, null);
    }

    private static UserPrompt textPrompt(final String content) {
        return UserPrompt.text("session-1", "run-1", content, NOW);
    }

    @Test
    void duplicateImagePromptsAreAllReplacedWhenOverBudget() {
        final var processor = new ImageLimitPreProcessor(1);
        final var image = imagePrompt("data:image/png;base64,aGVsbG8=");
        final var imageCopy = imagePrompt("data:image/png;base64,aGVsbG8=");
        // Two equal-by-value image prompts: the older instance is replaced, the newer is kept
        final var allMessages = List.<AgentMessage>of(systemPrompt(), image, imageCopy);

        final var result = processor.process(null, allMessages, List.copyOf(allMessages));

        final var transformed = result.getTransformedMessages();
        assertEquals(3, transformed.size());
        final var replaced = assertInstanceOf(UserPrompt.class, transformed.get(1));
        assertEquals(MessageContentType.TEXT, replaced.getContentType());
        assertSame(imageCopy, transformed.get(2));
    }

    @Test
    void imageCountWithinLimitIsANoOp() {
        final var processor = new ImageLimitPreProcessor(2);
        final var allMessages = List.<AgentMessage>of(systemPrompt(),
                                                      imagePrompt("data:image/png;base64,aGVsbG8="),
                                                      textPrompt("hello"));
        final var newMessages = List.<AgentMessage>of(imagePrompt("data:image/png;base64,aGVsbG8="));

        final var result = processor.process(null, allMessages, newMessages);

        assertSame(allMessages, result.getTransformedMessages());
        assertSame(newMessages, result.getNewMessages());
    }

    @Test
    void negativeMaxImagesIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ImageLimitPreProcessor(-1));
    }

    @Test
    void newMessagesWithoutImagesAreUnchanged() {
        final var processor = new ImageLimitPreProcessor(0);
        final var image = imagePrompt("data:image/png;base64,aGVsbG8=");
        final var allMessages = new ArrayList<AgentMessage>(List.of(systemPrompt(), image));
        final var newMessages = List.<AgentMessage>of(textPrompt("new text"));

        final var result = processor.process(null, allMessages, newMessages);

        assertEquals(newMessages, result.getNewMessages());
        assertEquals(2, result.getTransformedMessages().size());
        assertNotNull(result.getTransformedMessages().get(1));
        assertTrue(result.getTransformedMessages().get(1) instanceof UserPrompt);
    }

    @Test
    void oldestImagesAreReplacedAndNewestAreKept() {
        final var processor = new ImageLimitPreProcessor(2);
        final var image1 = imagePrompt("data:image/png;base64,MQ==");
        final var image2 = imagePrompt("data:image/png;base64,Mg==");
        final var image3 = imagePrompt("data:image/png;base64,Mw==");
        final var text = textPrompt("between images");
        final var allMessages = List.<AgentMessage>of(systemPrompt(), image1, text, image2, image3);

        final var result = processor.process(null, allMessages, List.copyOf(allMessages));

        final var transformed = result.getTransformedMessages();
        assertEquals(5, transformed.size());
        // Oldest image is replaced with a text placeholder
        final var replaced = assertInstanceOf(UserPrompt.class, transformed.get(1));
        assertEquals(MessageContentType.TEXT, replaced.getContentType());
        assertEquals("[image omitted: %s]".formatted(image1.getMessageId()), replaced.getContent());
        // Positions and non-image messages stay intact
        assertSame(text, transformed.get(2));
        // Newest images are kept as-is
        assertSame(image2, transformed.get(3));
        assertSame(image3, transformed.get(4));

        // newMessages get the same replacements
        assertEquals(transformed, result.getNewMessages());
    }

    @Test
    void zeroMaxImagesReplacesAllImagesWithPlaceholders() {
        final var processor = new ImageLimitPreProcessor(0);
        final var image = imagePrompt("data:image/png;base64,aGVsbG8=");
        final var allMessages = List.<AgentMessage>of(systemPrompt(), image);

        final var result = processor.process(null, allMessages, List.copyOf(allMessages));

        assertEquals(2, result.getTransformedMessages().size());
        assertSame(allMessages.get(0), result.getTransformedMessages().get(0));
        final var placeholder = assertInstanceOf(UserPrompt.class, result.getTransformedMessages().get(1));
        assertEquals(MessageContentType.TEXT, placeholder.getContentType());
        assertEquals("[image omitted: %s]".formatted(image.getMessageId()), placeholder.getContent());
        assertEquals(image.getMessageId(), placeholder.getMessageId());
    }
}
