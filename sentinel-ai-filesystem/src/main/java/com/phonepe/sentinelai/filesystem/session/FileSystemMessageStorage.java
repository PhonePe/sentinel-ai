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

package com.phonepe.sentinelai.filesystem.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.filesystem.utils.FileUtils;
import com.phonepe.sentinelai.session.BiScrollable;
import com.phonepe.sentinelai.session.BiScrollable.DataPointer;
import com.phonepe.sentinelai.session.QueryDirection;

import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Consumer;

/**
 * Disk based storage for messages.
 * Implementation:
 * - All messages are stored in a jsonl file called messages.jsonl in the provided directory.
 * - Each line in the file represents a single message in JSON format.
 * - Messages are stored in chronological order (oldest to newest).
 * - We maintain a treemap based index for fast fetch
 * - stamped lock based locking to ensure
 * - We use the map key as the pointers in BiScrollable
 * - All messages from file are read in one shot at startup and served from the treemap
 * - Writes are appended to file and committed to the treemap for access
 */
public class FileSystemMessageStorage {

    private static final String MESSAGES_FILE_NAME = "messages.jsonl";

    private record MessageMeta(
            String messageId,
            long timestamp
    ) {
    }

    private final TreeMap<MessageMeta, AgentMessage> messageCache;

    private final StampedLock lock = new StampedLock();

    private final ObjectMapper objectMapper;

    private final Path filePath;

    @SneakyThrows
    @Builder
    public FileSystemMessageStorage(@NonNull String sessionDir, @NonNull ObjectMapper objectMapper) {
        this.messageCache = new TreeMap<>(Comparator.comparing(MessageMeta::timestamp)
                .thenComparing(MessageMeta::messageId));
        // Load existing messages from file into cache and be done and dusted with
        // the reading part
        readMessagesFromFile(sessionDir, objectMapper, msg -> {
            final var meta = new MessageMeta(msg.getMessageId(), msg.getTimestamp());
            messageCache.put(meta, msg);
        });
        this.objectMapper = objectMapper;
        this.filePath = ensureMessageFile(sessionDir);
    }

    /**
     * Simple write Operation.
     * We do not overcomplicate with batching or async writes here because this is supposed to be written to at end of a
     * run. A run would be long lived, which means that writes will be spaced out. So we use a relatively expensive
     * atomic write operation to ensure data integrity and consistency between file and cache.
     */
    @SneakyThrows
    public void addMessages(List<AgentMessage> messages) {
        final var stamp = lock.writeLock();
        try {
            final var messageData = new ByteArrayOutputStream();
            final var newMessages = new HashMap<MessageMeta, AgentMessage>();
            messages.forEach(message -> {
                final var meta = new MessageMeta(message.getMessageId(), message.getTimestamp());
                try {
                    messageData.write((objectMapper.writeValueAsString(message) + System.lineSeparator())
                            .getBytes(StandardCharsets.UTF_8));
                    newMessages.put(meta, message);
                }
                catch (Exception e) {
                    throw new RuntimeException("Failed to serialize message: " + message, e);
                }
            });
            messageData.flush();
            final var data = messageData.toByteArray();
            if (FileUtils.write(filePath, data, true)) {
                messageCache.putAll(newMessages);
            }
            else {
                throw new RuntimeException("Failed to write message to file: " + filePath.toAbsolutePath());
            }
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    /**
     * Reads messages for a specific session with pagination support.
     * This method helps clients keep their state management simpler. Basically the same
     * {@link com.phonepe.sentinelai.session.BiScrollable.DataPointer} can be passed back to the server to get the
     * next set of messages in both directions. Messages are always returned in chronological order (oldest to newest).
     *
     * @param count            The maximum number of messages to retrieve.
     * @param skipSystemPrompt If true, system prompt request messages will be excluded from the result.
     * @param pointer          The {@link com.phonepe.sentinelai.session.BiScrollable.DataPointer} used by the client
     *                         to indicate the current position in the message list.
     * @param queryDirection   The direction to scroll in: {@link QueryDirection#OLDER} to fetch messages before the
     *                         pointer,
     *                         or {@link QueryDirection#NEWER} to fetch messages after the pointer.
     * @return A {@link BiScrollable} containing the list of messages (sorted chronologically) and pointers for
     *         further scrolling.
     */
    @SneakyThrows
    public BiScrollable<AgentMessage> readMessages(int count,
                                                   boolean skipSystemPrompt,
                                                   BiScrollable.DataPointer pointer,
                                                   QueryDirection queryDirection) {
        final var olderPointerStr = AgentUtils.getIfNotNull(pointer, DataPointer::getOlder, null);
        final var newerPointerStr = AgentUtils.getIfNotNull(pointer, DataPointer::getNewer, null);
        final var relevantPointer = queryDirection == QueryDirection.OLDER ? olderPointerStr : newerPointerStr;
        MessageMeta actualPointer = null;
        if (!Strings.isNullOrEmpty(relevantPointer)) {
            actualPointer = objectMapper.readValue(Base64.getDecoder().decode(relevantPointer), MessageMeta.class);
        }
        final var stamp = lock.readLock();
        try {
            final var messages = switch (queryDirection) {
                case NEWER -> {
                    final var subMap = actualPointer == null
                            ? messageCache
                            : messageCache.tailMap(actualPointer, false);
                    yield subMap.values()
                            .stream()
                            .filter(msg -> !skipSystemPrompt
                                    || msg.getMessageType() != AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE)
                            .limit(count)
                            .toList();
                }
                case OLDER -> {
                    final var subMap = actualPointer == null
                            ? messageCache
                            : messageCache.headMap(actualPointer, false);
                    final var list = subMap.descendingMap()
                            .values()
                            .stream()
                            .filter(msg -> !skipSystemPrompt
                                    || msg.getMessageType() != AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE)
                            .limit(count)
                            .toList();
                    yield list.stream()
                            .sorted(Comparator.comparing(AgentMessage::getTimestamp)
                                    .thenComparing(AgentMessage::getMessageId))
                            .toList();
                }
            };
            final var firstMsg = messages.isEmpty() ? null : messages.get(0);
            final var lastMsg = messages.isEmpty() ? null : messages.get(messages.size() - 1);
            final var outPtr = switch (queryDirection) {
                case NEWER -> {
                    final var latestPtr = lastMsg != null
                            ? pointerStr(new MessageMeta(lastMsg.getMessageId(),
                                                         lastMsg.getTimestamp()))
                            : newerPointerStr;
                    final var oldestPtr = (olderPointerStr == null && firstMsg != null)
                            ? pointerStr(new MessageMeta(firstMsg.getMessageId(),
                                                         firstMsg.getTimestamp()))
                            : olderPointerStr;
                    yield new DataPointer(oldestPtr, latestPtr);
                }
                case OLDER -> {
                    final var oldestPtr = firstMsg != null
                            ? pointerStr(new MessageMeta(firstMsg.getMessageId(),
                                                         firstMsg.getTimestamp()))
                            : olderPointerStr;
                    final var latestPtr = (newerPointerStr == null && lastMsg != null)
                            ? pointerStr(new MessageMeta(lastMsg.getMessageId(),
                                                         lastMsg.getTimestamp()))
                            : newerPointerStr;
                    yield new DataPointer(oldestPtr, latestPtr);
                }
            };
            return BiScrollable.<AgentMessage>builder()
                    .items(List.copyOf(messages))
                    .pointer(outPtr)
                    .build();
        }
        finally {
            lock.unlockRead(stamp);
        }
    }

    @SneakyThrows
    public boolean purgeMessages(String sessionId) {
        final var stamp = lock.writeLock();
        try {
            if (Files.deleteIfExists(filePath)) {
                messageCache.clear();
                return true;
            }
            return false;
        }
        finally {
            lock.unlockWrite(stamp);
        }
    }

    @SneakyThrows
    private static Path ensureMessageFile(String sessionDir) {
        final var dirPath = Path.of(sessionDir);
        if (!Files.exists(dirPath, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(dirPath)
                || !Files.isWritable(dirPath)) {
            throw new IllegalArgumentException("Provided sessionDir must be an existing writable directory");
        }
        final var filePath = dirPath.resolve(MESSAGES_FILE_NAME);
        if (Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)
                && !Files.isWritable(filePath)) {
            throw new IllegalArgumentException("messages.jsonl file is not writable");
        }
        return filePath;
    }

    @SneakyThrows
    private void readMessagesFromFile(String sessionDir,
                                      final ObjectMapper objectMapper,
                                      Consumer<AgentMessage> messageConsumer) {
        if (!Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
            for (String line : (Iterable<String>) lines::iterator) {
                try {
                    messageConsumer.accept(objectMapper.readValue(line, AgentMessage.class));
                }
                catch (Exception e) {
                    throw new RuntimeException("Failed to parse message from file: " + line, e);
                }
            }
        }
    }

    @SneakyThrows
    private String pointerStr(final MessageMeta meta) {
        if (null == meta) {
            return null;
        }
        return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(meta));
    }

}
