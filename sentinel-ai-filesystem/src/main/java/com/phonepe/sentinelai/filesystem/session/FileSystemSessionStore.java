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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class FileSystemSessionStore implements SessionStore {
    private final DiskBasedSessionSummaryStore summaryStore;

    public FileSystemSessionStore(String baseDir, final ObjectMapper mapper) {
        this.summaryStore = new DiskBasedSessionSummaryStore(baseDir, mapper, 20);
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
                .orElseThrow(() -> new IllegalStateException("Message storage not found for session: " + sessionId))
                .readMessages(count, skipSystemPrompt, pointer, queryDirection);
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
    public BiScrollable<SessionSummary> sessions(int count, String pointer, QueryDirection queryDirection) {
        final var allSummaries = summaryStore.listSessionSummaries()
                .stream()
                .sorted(Comparator.comparing(SessionSummary::getUpdatedAt))
                .toList();
        return new BiScrollable(allSummaries, new DataPointer(null, null));
    }


}
