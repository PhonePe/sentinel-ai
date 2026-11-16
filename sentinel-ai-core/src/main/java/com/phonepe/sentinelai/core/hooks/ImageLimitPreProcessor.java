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

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.MediaTypes.MessageContentType;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;

import lombok.extern.slf4j.Slf4j;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * An {@link AgentMessagesPreProcessor} that caps the number of image prompts sent to a model
 * in a single request.
 *
 * <p>Every model request resends the full message history, so images from earlier turns count
 * against the provider image limit each time. When the number of image prompts exceeds
 * {@code maxImages}, this processor keeps the newest {@code maxImages} images and converts the
 * older ones to text placeholders such as {@code [image omitted: <messageId>]}. Converting an
 * image prompt to a text prompt keeps the message positions stable, so tool-call and
 * tool-response pairings stay intact.
 *
 * <p>Non-image messages are never modified. When the image count is within the limit this
 * processor is a no-op and returns the input lists unchanged.
 */
@Slf4j
public class ImageLimitPreProcessor implements AgentMessagesPreProcessor {

    private final int maxImages;

    /**
     * Creates a processor that allows at most {@code maxImages} image prompts per request.
     *
     * @param maxImages maximum number of image prompts to keep. Use {@code 0} to replace all
     *                  image prompts with text placeholders. Negative values are rejected.
     */
    public ImageLimitPreProcessor(final int maxImages) {
        if (maxImages < 0) {
            throw new IllegalArgumentException("maxImages must be zero or more, got: " + maxImages);
        }
        this.maxImages = maxImages;
    }

    private static boolean isImagePrompt(final AgentMessage message) {
        return message instanceof UserPrompt userPrompt
                && (userPrompt.getContentType() == MessageContentType.IMAGE_DATA
                        || userPrompt.getContentType() == MessageContentType.IMAGE_URL);
    }

    private static List<AgentMessage> replace(final List<AgentMessage> messages,
                                              final Map<AgentMessage, AgentMessage> replacements) {
        if (messages == null || replacements.isEmpty()) {
            return messages;
        }
        return messages.stream()
                .map(message -> replacements.containsKey(message) ? replacements.get(message) : message)
                .toList();
    }

    private static UserPrompt textPlaceholder(final UserPrompt image) {
        return UserPrompt.builder()
                .sessionId(image.getSessionId())
                .runId(image.getRunId())
                .messageId(image.getMessageId())
                .timestamp(image.getTimestamp())
                .contentType(MessageContentType.TEXT)
                .content("[image omitted: %s]".formatted(image.getMessageId()))
                .compacted(false)
                .sentAt(image.getSentAt())
                .build();
    }

    @Override
    public AgentMessagesPreProcessResult process(final AgentMessagesPreProcessContext ctx,
                                                 final List<AgentMessage> allMessages,
                                                 final List<AgentMessage> newMessages) {
        final var imageCount = allMessages.stream()
                .filter(ImageLimitPreProcessor::isImagePrompt)
                .count();
        if (imageCount <= maxImages) {
            return new AgentMessagesPreProcessResult(allMessages, newMessages);
        }
        log.debug("Capping image prompts from {} to the newest {}", imageCount, maxImages);
        final var replacements = planReplacements(allMessages);
        return new AgentMessagesPreProcessResult(replace(allMessages, replacements),
                                                 replace(newMessages, replacements));
    }

    /**
     * Walks the messages from newest to oldest and maps every image prompt beyond the budget to
     * a text placeholder. Message equality is value-based, so the replacements use an identity
     * map to target exact message instances.
     */
    private Map<AgentMessage, AgentMessage> planReplacements(final List<AgentMessage> allMessages) {
        final var replacements = new IdentityHashMap<AgentMessage, AgentMessage>();
        var remaining = maxImages;
        for (var i = allMessages.size() - 1; i >= 0; i--) {
            final var message = allMessages.get(i);
            if (!isImagePrompt(message)) {
                continue;
            }
            if (remaining > 0) {
                remaining--;
                continue;
            }
            replacements.put(message, textPlaceholder((UserPrompt) message));
        }
        return replacements;
    }
}
