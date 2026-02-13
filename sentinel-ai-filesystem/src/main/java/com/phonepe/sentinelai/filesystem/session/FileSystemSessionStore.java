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

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.session.BiScrollable;
import com.phonepe.sentinelai.session.BiScrollable.DataPointer;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionStore;
import com.phonepe.sentinelai.session.SessionSummary;

import lombok.SneakyThrows;

import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class FileSystemSessionStore implements SessionStore {
    private final DiskBasedSessionSummaryStore summaryStore;
    private final ObjectMapper mapper;

    public FileSystemSessionStore(String baseDir, final ObjectMapper mapper) {
        this(baseDir, mapper, 20);
    }

    public FileSystemSessionStore(String baseDir, final ObjectMapper mapper, int cacheSize) {
        this.summaryStore = new DiskBasedSessionSummaryStore(baseDir, mapper, cacheSize);
        this.mapper = mapper;
    }

    @Override
    public boolean deleteSession(String sessionId) {
        return summaryStore.deleteSession(sessionId);
    }

    @Override
    public BiScrollable<AgentMessage> readMessages(String sessionId,
                                                   int count,
                                                   boolean skipSystemPrompt,
                                                   DataPointer pointer,
                                                   QueryDirection queryDirection) {
        return summaryStore.getMessageStorage(sessionId)
                .map(storage -> storage.readMessages(count, skipSystemPrompt, pointer, queryDirection))
                .orElseGet(() -> new BiScrollable<>(List.of(), new DataPointer(null, null)));
    }

    @Override
    public void saveMessages(String sessionId, String runId, List<AgentMessage> messages) {
        summaryStore.getMessageStorage(sessionId)
                .orElseThrow(() -> new IllegalStateException("Message storage not found for session: " + sessionId))
                .addMessages(messages);
    }

    @Override
    @SneakyThrows
    public Optional<SessionSummary> saveSession(SessionSummary sessionSummary) {
        summaryStore.saveSummary(sessionSummary);
        return Optional.of(sessionSummary);
    }

    @Override
    public Optional<SessionSummary> session(String sessionId) {
        return summaryStore.sessionSummary(sessionId);
    }

    @Override
    @SneakyThrows
    @SuppressWarnings("java:S3776")
    public BiScrollable<SessionSummary> sessions(int count, String pointer, QueryDirection queryDirection) {
        final var scrollPointer = (pointer == null || pointer.isEmpty())
                ? null
                : mapper.readValue(Base64.getDecoder().decode(pointer), SessionScrollPointer.class);
        final var allSummaries = summaryStore.listSessionSummaries();
        final var comparator = queryDirection == QueryDirection.NEWER
                ? Comparator.comparingLong(SessionSummary::getUpdatedAt)
                        .thenComparing(SessionSummary::getSessionId)
                : Comparator.comparingLong(SessionSummary::getUpdatedAt)
                        .thenComparing(SessionSummary::getSessionId)
                        .reversed();

        final var filteredSummaries = allSummaries.stream()
                .filter(summary -> {
                    if (scrollPointer == null) {
                        return true;
                    }
                    if (queryDirection == QueryDirection.NEWER) {
                        return summary.getUpdatedAt() > scrollPointer.timestamp()
                                || (summary.getUpdatedAt() == scrollPointer.timestamp()
                                        && summary.getSessionId().compareTo(scrollPointer.id()) > 0);
                    }
                    else {
                        return summary.getUpdatedAt() < scrollPointer.timestamp()
                                || (summary.getUpdatedAt() == scrollPointer.timestamp()
                                        && summary.getSessionId().compareTo(scrollPointer.id()) < 0);
                    }
                })
                .sorted(comparator)
                .limit(count)
                .toList();

        if (filteredSummaries.isEmpty()) {
            return new BiScrollable<>(List.of(), new DataPointer(null, null));
        }

        final var firstSummary = filteredSummaries.get(0);
        final var lastSummary = filteredSummaries.get(filteredSummaries.size() - 1);

        final var firstPtr = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(
                                                                                         new SessionScrollPointer(firstSummary
                                                                                                 .getUpdatedAt(),
                                                                                                                  firstSummary
                                                                                                                          .getSessionId())));
        final var lastPtr = Base64.getEncoder().encodeToString(mapper.writeValueAsBytes(
                                                                                        new SessionScrollPointer(lastSummary
                                                                                                .getUpdatedAt(),
                                                                                                                 lastSummary
                                                                                                                         .getSessionId())));

        final var oldestResultPtr = (queryDirection == QueryDirection.NEWER) ? firstPtr : lastPtr;
        final var newestResultPtr = (queryDirection == QueryDirection.NEWER) ? lastPtr : firstPtr;

        final var updatedOlderForNewQuery = pointer == null ? oldestResultPtr : null;
        final var older = queryDirection == QueryDirection.OLDER ? oldestResultPtr : updatedOlderForNewQuery;

        final var updatedNewerForOlderQuery = pointer == null ? newestResultPtr : null;
        final var newer = queryDirection == QueryDirection.NEWER ? newestResultPtr : updatedNewerForOlderQuery;

        return new BiScrollable<>(filteredSummaries, new DataPointer(older, newer));
    }


    private record SessionScrollPointer(
            long timestamp,
            String id
    ) {
    }


}
