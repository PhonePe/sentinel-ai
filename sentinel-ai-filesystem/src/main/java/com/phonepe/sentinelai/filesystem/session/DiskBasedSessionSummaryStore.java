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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.filesystem.utils.FileUtils;
import com.phonepe.sentinelai.session.SessionSummary;

import lombok.Data;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.StampedLock;

@Slf4j
public class DiskBasedSessionSummaryStore {
    private static final String SUMMARY_FILE_NAME = "summary.json";

    @Data
    private static final class SessionContainer {
        private SessionSummary sessionSummary = null;
        private FileSystemMessageStorage messageStorage = null;
    }

    private final Path sessionRoot;
    private final ObjectMapper objectMapper;
    private final Map<String, SessionContainer> cache;
    private final StampedLock cacheLock = new StampedLock();

    public DiskBasedSessionSummaryStore(
                                        @NonNull final String sessionDir,
                                        @NonNull final ObjectMapper objectMapper,
                                        int cacheSize) {
        this.cache = new LinkedHashMap<>(cacheSize, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SessionContainer> eldest) {
                if (size() > cacheSize) {
                    eldest.getValue().setMessageStorage(null);
                    return true;
                }
                return false;
            }
        };
        this.sessionRoot = FileUtils.ensurePath(sessionDir, true, true);
        this.objectMapper = objectMapper;
    }

    @SneakyThrows
    public boolean deleteSession(final String sessionId) {
        final var sessionDir = sessionRoot.resolve(sessionId);
        final var writeLock = cacheLock.writeLock();
        try {
            return null == cache.computeIfPresent(sessionId, (id, existing) -> {
                try {
                    if (Files.exists(sessionDir, LinkOption.NOFOLLOW_LINKS)) {
                        try (final var children = Files.walk(sessionDir)) {
                            children.sorted(Comparator.reverseOrder())
                                    .forEach(path -> {
                                        try {
                                            Files.deleteIfExists(path);
                                        }
                                        catch (Exception e) {
                                            throw new RuntimeException("Failed to delete file: " + path, e);
                                        }
                                    });

                        }
                        return null; //Deleted successfully, remove from cache
                    }
                }
                catch (Exception e) {
                    throw new RuntimeException("Failed to delete session summary for session: " + id, e);
                }
                return existing; //Could not delete, retain in cache
            });
        }
        finally {
            cacheLock.unlockWrite(writeLock);
        }
    }

    public Optional<FileSystemMessageStorage> getMessageStorage(String sessionId) {
        var stamp = cacheLock.readLock();
        try {
            final var existingStorage = AgentUtils.getIfNotNull(cache.get(sessionId),
                                                                SessionContainer::getMessageStorage,
                                                                null);
            if (null != existingStorage) {
                return Optional.of(existingStorage);
            }
            stamp = cacheLock.tryConvertToWriteLock(stamp);
            if (stamp == 0L) {
                // Could not convert to write lock, release read lock and acquire write lock
                cacheLock.unlockRead(stamp);
                stamp = cacheLock.writeLock();
            }
            final var sessionContainer = cache.compute(sessionId, (id, existing) -> {
                final var session = Objects.requireNonNullElseGet(existing,
                                                                  () -> new SessionContainer());
                if (session.getMessageStorage() == null) {
                    final var messagePath = sessionRoot.resolve(id)
                            .toAbsolutePath()
                            .normalize()
                            .toString();
                    log.debug("Initializing message storage for session: {}, path: {}", id, messagePath);
                    session.setMessageStorage(new FileSystemMessageStorage(messagePath, objectMapper));
                }
                return session;
            });
            return Optional.ofNullable(AgentUtils.getIfNotNull(sessionContainer,
                                                               SessionContainer::getMessageStorage,
                                                               null));
        }
        finally {
            cacheLock.unlock(stamp);
        }
    }

    @SneakyThrows
    public List<SessionSummary> listSessionSummaries() {
        try (final var sessionDirs = Files.list(sessionRoot)) {
            return sessionDirs
                    .filter(Files::isDirectory)
                    .map(path -> sessionSummary(path.getFileName().toString()).orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    @SneakyThrows
    public boolean saveSummary(SessionSummary sessionSummary) {

        final var sessionId = sessionSummary.getSessionId();
        final var sessionDir = FileUtils.ensurePath(
                                                    sessionRoot.resolve(sessionId).toString(),
                                                    true,
                                                    true);

        final var sessionFilePath = sessionDir.resolve(SUMMARY_FILE_NAME);
        final var writeLock = cacheLock.writeLock();
        try {
            final var updated = cache.compute(sessionId, (id, existingSummary) -> {
                final var session = Objects.requireNonNullElseGet(existingSummary, SessionContainer::new);
                try {
                    if (FileUtils.write(sessionFilePath,
                                        objectMapper.writeValueAsBytes(sessionSummary),
                                        false)) {
                        return session.setSessionSummary(sessionSummary);
                    }
                }
                catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to serialize session summary for session: " + id, e);
                }
                return session;
            });
            final var session = AgentUtils.getIfNotNull(updated, SessionContainer::getSessionSummary, null);
            return session != null
                    && session.getSessionId().equals(sessionId)
                    && session.getUpdatedAt() == sessionSummary.getUpdatedAt();
        }
        finally {
            cacheLock.unlockWrite(writeLock);
        }
    }

    public Optional<SessionSummary> sessionSummary(final String sessionId) {
        final var stamp = cacheLock.writeLock();
        try {
            final var sessionContainer = loadSessionContainerUnsafe(sessionId).orElse(null);
            return Optional.ofNullable(AgentUtils.getIfNotNull(sessionContainer,
                                                               SessionContainer::getSessionSummary,
                                                               null));
        }
        finally {
            cacheLock.unlockWrite(stamp);
        }
    }

    private Optional<SessionContainer> loadSessionContainerUnsafe(final String sessionId) {
        return Optional.ofNullable(cache.computeIfAbsent(sessionId, id -> {
            final var sessionFilePath = sessionRoot.resolve(id).resolve(SUMMARY_FILE_NAME);
            if (Files.exists(sessionFilePath, LinkOption.NOFOLLOW_LINKS)) {
                try {
                    return new SessionContainer()
                            .setSessionSummary(objectMapper.readValue(sessionFilePath.toFile(),
                                                                      SessionSummary.class));
                }
                catch (Exception e) {
                    throw new RuntimeException("Failed to read session summary for session: " + id, e);
                }
            }
            return null;
        }));
    }

}
