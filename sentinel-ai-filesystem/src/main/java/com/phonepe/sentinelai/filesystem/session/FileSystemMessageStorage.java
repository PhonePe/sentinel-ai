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
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
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
import java.util.function.Predicate;

/**
 * Disk based storage for messages.
 * Implementation:
 * - All messages are stored in a jsonl file called messages.jsonl in the provided directory.
 * - Each line in the file represents a single message in JSON format.
 * - Messages are stored in chronological order (oldest to newest).
 * - We maintain a treemap based index for fast fetch
 * - stamped lock based locking to ensure
 * - We use the map key as the pointers in BiScrollable
 * - Messages are loaded lazily: an OLDER query with no pointer first loads only the tail of the file,
 * and a full load happens only when a query needs messages outside the loaded tail
 * - Writes are appended to file and committed to the treemap for access
 */
@Slf4j
public class FileSystemMessageStorage {

    private static final String MESSAGES_FILE_NAME = "messages.jsonl";

    private static final int TAIL_LOAD_CHUNK_SIZE = 64 * 1024;

    private static final int MAX_TAIL_LOAD_LINES = 10_000;

    private enum LoadState {
        NOT_LOADED,
        TAIL_LOADED,
        FULLY_LOADED
    }

    private record MessageMeta(
            String messageId,
            long timestamp
    ) {
    }

    private final TreeMap<MessageMeta, AgentMessage> messageCache;

    private final StampedLock lock = new StampedLock();
    private LoadState loadState = LoadState.NOT_LOADED;

    private final String sessionDir;
    private final ObjectMapper objectMapper;

    private final Path filePath;

    @SneakyThrows
    @Builder
    public FileSystemMessageStorage(@NonNull String sessionDir, @NonNull ObjectMapper objectMapper) {
        this.messageCache = new TreeMap<>(Comparator.comparing(MessageMeta::timestamp)
                .thenComparing(MessageMeta::messageId));
        this.sessionDir = sessionDir;
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
        try (final var messageData = new ByteArrayOutputStream()) {
            final var newMessages = new HashMap<MessageMeta, AgentMessage>();
            messages.forEach(message -> {
                final var meta = new MessageMeta(message.getMessageId(), message.getTimestamp());
                populate(message, messageData);
                newMessages.put(meta, message);
            });
            messageData.flush();
            final var data = messageData.toByteArray();
            if (FileUtils.write(filePath, data, true)) {
                messageCache.putAll(newMessages);
            }
            else {
                throw new IllegalStateException("Failed to write messages to file: %s"
                        .formatted(filePath.toAbsolutePath()));
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
        var stamp = lock.readLock();
        try {
            stamp = ensureMessagesLoadedUnsafe(stamp, queryDirection, actualPointer, skipSystemPrompt, count);
            final var messages = readFromLoadedMessagesUnsafe(queryDirection, actualPointer, skipSystemPrompt, count);
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
            lock.unlock(stamp);
        }
    }

    @SneakyThrows
    public boolean purgeMessages(String sessionId) {
        final var stamp = lock.writeLock();
        try {
            if (Files.deleteIfExists(filePath)) {
                messageCache.clear();
                loadState = LoadState.NOT_LOADED;
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
        if (Files.exists(dirPath, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(dirPath)
                    || !Files.isWritable(dirPath)) {
                throw new IllegalArgumentException("Provided sessionDir %s must be an existing writable directory"
                        .formatted(dirPath.toAbsolutePath().normalize()));
            }
        }
        else {
            Files.createDirectories(dirPath);
        }
        final var filePath = dirPath.resolve(MESSAGES_FILE_NAME);
        if (Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)
                && !Files.isWritable(filePath)) {
            throw new IllegalArgumentException("messages.jsonl file is not writable");
        }
        return filePath;
    }

    private List<AgentMessage> readFromLoadedMessagesUnsafe(final QueryDirection queryDirection,
                                                            final MessageMeta actualPointer,
                                                            boolean skipSystemPrompt,
                                                            int count) {
        final var visibilityFilter = visibilityFilter(skipSystemPrompt);
        return switch (queryDirection) {
            case NEWER -> {
                final var subMap = actualPointer == null
                        ? messageCache
                        : messageCache.tailMap(actualPointer, false);
                yield subMap.values()
                        .stream()
                        .filter(visibilityFilter)
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
                        .filter(visibilityFilter)
                        .limit(count)
                        .toList();
                yield list.stream()
                        .sorted(Comparator.comparing(AgentMessage::getTimestamp)
                                .thenComparing(AgentMessage::getMessageId))
                        .toList();
            }
        };

    }

    private static Predicate<AgentMessage> visibilityFilter(boolean skipSystemPrompt) {
        return msg -> !skipSystemPrompt
                || msg.getMessageType() != AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE;
    }

    /**
     * Makes sure enough messages are in the cache to answer the given query.
     * Strategy:
     * - FULLY_LOADED: nothing to do.
     * - NOT_LOADED: an OLDER query with no pointer first loads only the tail of the file;
     * any other query does a full load directly.
     * - TAIL_LOADED: a query the tail cannot answer (NEWER direction, pointer below the tail,
     * or too few visible messages) triggers a full load.
     * After a full load the cache holds every message in the file.
     */
    private long ensureMessagesLoadedUnsafe(long originalStamp,
                                            QueryDirection queryDirection,
                                            MessageMeta actualPointer,
                                            boolean skipSystemPrompt,
                                            int count) {
        var stamp = originalStamp;
        if (LoadState.FULLY_LOADED == loadState) {
            return stamp;
        }
        // Upgrade to write lock to safely load messages from disk
        long ws = lock.tryConvertToWriteLock(stamp);
        if (ws != 0) {
            stamp = ws;
        }
        else {
            // Other readers present — release read lock and acquire write lock
            lock.unlockRead(stamp);
            stamp = lock.writeLock();
        }
        // Double-checked: another thread may have changed the state while we were waiting
        if (LoadState.NOT_LOADED == loadState) {
            if (QueryDirection.OLDER == queryDirection && actualPointer == null) {
                readTailFromFileUnsafe(skipSystemPrompt, count);
            }
            else {
                fullLoadUnsafe();
            }
        }
        else if (LoadState.TAIL_LOADED == loadState
                && !tailCanAnswerUnsafe(queryDirection, actualPointer, skipSystemPrompt, count)) {
                    fullLoadUnsafe();
                }
        // Downgrade back to read lock for the rest of the method
        final var rs = lock.tryConvertToReadLock(stamp);
        if (rs == 0) {
            // Conversion failed (e.g. other threads waiting for write lock); release and re-acquire read lock
            lock.unlockWrite(stamp);
            stamp = lock.readLock();
        }
        else {
            stamp = rs;
        }
        return stamp;
    }

    private boolean tailCanAnswerUnsafe(QueryDirection queryDirection,
                                        MessageMeta actualPointer,
                                        boolean skipSystemPrompt,
                                        int count) {
        if (QueryDirection.NEWER == queryDirection) {
            // The tail is a suffix of the file; it can answer only queries anchored inside it
            return actualPointer != null && messageCache.containsKey(actualPointer);
        }
        // The tail can answer an OLDER query only when the cache holds enough visible
        // messages on the older side of the pointer
        final var subMap = actualPointer == null
                ? messageCache
                : messageCache.headMap(actualPointer, false);
        return subMap.values()
                .stream()
                .filter(visibilityFilter(skipSystemPrompt))
                .count() >= count;
    }

    private void fullLoadUnsafe() {
        messageCache.clear();
        readMessagesFromFile(msg -> {
            final var meta = new MessageMeta(msg.getMessageId(), msg.getTimestamp());
            messageCache.put(meta, msg);
        });
        loadState = LoadState.FULLY_LOADED;
    }

    /**
     * Reads the tail of the messages file backwards, in chunks, until the given number of visible
     * messages is parsed or the line bound is hit. Sets state to TAIL_LOADED when the tail holds
     * enough messages; sets FULLY_LOADED when the file start is reached. The caller falls back to
     * a full load when the loaded tail is not sufficient.
     */
    private void readTailFromFileUnsafe(boolean skipSystemPrompt, int count) {
        long fileEnd;
        try {
            fileEnd = Files.size(filePath);
        }
        catch (Exception e) {
            log.error("Error reading message file size: {}", filePath.toAbsolutePath(), e);
            loadState = LoadState.FULLY_LOADED;
            return;
        }
        if (fileEnd == 0) {
            loadState = LoadState.FULLY_LOADED;
            return;
        }
        log.info("Loading message tail from file: {}", filePath.toAbsolutePath());
        var visible = 0;
        var rawLines = 0;
        var pending = "";
        var chunkEnd = fileEnd;
        try (final var raf = new RandomAccessFile(filePath.toFile(), "r")) {
            while (chunkEnd > 0) {
                final var chunkStart = Math.max(0, chunkEnd - TAIL_LOAD_CHUNK_SIZE);
                final var chunk = new byte[(int) (chunkEnd - chunkStart)];
                raf.seek(chunkStart);
                raf.readFully(chunk);
                var chunkText = new String(chunk, StandardCharsets.UTF_8) + pending;
                if (chunkStart > 0) {
                    // Skip the last possibly partial UTF-8 character or line; the next chunk reads it fully
                    final var firstNewline = chunkText.indexOf('\n');
                    if (firstNewline < 0) {
                        // The whole chunk is one partial line; keep it as pending
                        pending = chunkText;
                        chunkEnd = chunkStart;
                        continue;
                    }
                    pending = chunkText.substring(0, firstNewline);
                    chunkText = chunkText.substring(firstNewline + 1);
                }
                else {
                    pending = "";
                }
                chunkEnd = chunkStart;
                final var lines = chunkText.split("\n", -1);
                for (var i = lines.length - 1; i >= 0; i--) {
                    final var line = lines[i];
                    rawLines++;
                    if (line.isBlank()) {
                        continue;
                    }
                    try {
                        final var message = readFromJson(line, AgentMessage.class);
                        final var meta = new MessageMeta(message.getMessageId(), message.getTimestamp());
                        messageCache.put(meta, message);
                        if (visibilityFilter(skipSystemPrompt).test(message)) {
                            visible++;
                        }
                    }
                    catch (Exception e) {
                        log.warn("Skipping corrupted message line: {}", e.getMessage());
                    }
                    if (visible >= count) {
                        loadState = LoadState.TAIL_LOADED;
                        return;
                    }
                }
                if (rawLines >= MAX_TAIL_LOAD_LINES) {
                    // The line bound is hit; older messages stay on disk for now
                    loadState = LoadState.TAIL_LOADED;
                    return;
                }
            }
        }
        catch (Exception e) {
            log.error("Error reading message tail from file: {}", filePath.toAbsolutePath(), e);
        }
        // File start reached: every message in the file is now in the cache
        loadState = LoadState.FULLY_LOADED;
    }

    @SneakyThrows
    private void readMessagesFromFile(final Consumer<AgentMessage> messageConsumer) {
        if (!Files.exists(filePath, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        log.info("Loading existing messages from file: {}", filePath.toAbsolutePath());
        try (final var lines = Files.lines(filePath, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                if (Strings.isNullOrEmpty(line) || line.isBlank()) {
                    return; // Skip empty or blank lines
                }
                try {
                    final var message = readFromJson(line, AgentMessage.class);
                    messageConsumer.accept(message);
                }
                catch (Exception e) {
                    log.warn("Skipping corrupted message line: {}", e.getMessage());
                }
            });
        }
        catch (Exception e) {
            log.error("Error reading messages from file: {}", filePath.toAbsolutePath(), e);
        }
    }

    @SneakyThrows
    private String pointerStr(final MessageMeta meta) {
        if (null == meta) {
            return null;
        }
        return Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(meta));
    }

    @SneakyThrows
    private <T> T readFromJson(String json, Class<T> clazz) {
        return objectMapper.readValue(json, clazz);
    }

    @SneakyThrows
    private void populate(Object object, final ByteArrayOutputStream output) {
        output.write((objectMapper.writeValueAsString(object) + System.lineSeparator())
                .getBytes(StandardCharsets.UTF_8));
    }

}
