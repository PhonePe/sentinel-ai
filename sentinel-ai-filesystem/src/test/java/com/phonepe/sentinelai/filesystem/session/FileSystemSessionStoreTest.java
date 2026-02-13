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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.session.BiScrollable;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionSummary;

import lombok.SneakyThrows;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemSessionStoreTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private FileSystemSessionStore sessionStore;

    @BeforeEach
    void setUp() {
        objectMapper = JsonUtils.createMapper();
        sessionStore = new FileSystemSessionStore(tempDir.toString(), objectMapper);
    }

    @Test
    @SneakyThrows
    void testBoundedCaching() {
        // Create a store with a small cache size
        final var cacheSize = 5;
        final var boundedStore = new FileSystemSessionStore(tempDir.resolve("bounded").toString(),
                                                            objectMapper,
                                                            cacheSize);

        // Save more sessions than the cache size
        final var sessionIds = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> {
                    final var id = "S-" + i;
                    boundedStore.saveSession(SessionSummary.builder()
                            .sessionId(id)
                            .updatedAt(AgentUtils.epochMicro())
                            .build());
                    // Add some messages to initialize storage
                    boundedStore.saveMessages(id,
                                              "run",
                                              List.of(Text.builder()
                                                      .sessionId(id)
                                                      .content("msg")
                                                      .stats(new ModelUsageStats())
                                                      .build()));
                    return id;
                }).toList();

        // Access all sessions to ensure they are in cache at some point
        for (String id : sessionIds) {
            boundedStore.readMessages(id, 1, false, null, QueryDirection.OLDER);
        }

        // The cache should only hold 'cacheSize' message storages.
        // However, we don't have direct access to the cache.
        // But we can verify that the sessions still exist on disk and can be reloaded.
        for (String id : sessionIds) {
            final var messages = boundedStore.readMessages(id, 1, false, null, QueryDirection.OLDER);
            assertFalse(messages.getItems().isEmpty());
        }
    }

    @Test
    @SneakyThrows
    void testSessionMessageStorage() {
        final var sessionId = "msg-session";
        final var runId = UUID.randomUUID().toString();

        final var messages = IntStream.rangeClosed(1, 50).mapToObj(i -> {
            if (i % 3 == 0) {
                return SystemPrompt.builder()
                        .sessionId(sessionId)
                        .runId(runId)
                        .content("system-" + i)
                        .timestamp(AgentUtils.epochMicro() + i)
                        .build();
            }
            if (i % 2 == 0) {
                return UserPrompt.builder()
                        .sessionId(sessionId)
                        .runId(runId)
                        .content("user-" + i)
                        .timestamp(AgentUtils.epochMicro() + i)
                        .build();
            }
            return Text.builder()
                    .sessionId(sessionId)
                    .runId(runId)
                    .content("text-" + i)
                    .stats(new ModelUsageStats())
                    .elapsedTimeMs(100)
                    .timestamp(AgentUtils.epochMicro() + i)
                    .build();
        }).toList();

        final var expectedIds = messages.stream()
                .map(AgentMessage::getMessageId)
                .collect(Collectors.toUnmodifiableSet());

        sessionStore.saveSession(SessionSummary.builder()
                .sessionId(sessionId)
                .updatedAt(AgentUtils.epochMicro())
                .build());
        sessionStore.saveMessages(sessionId, runId, messages);

        final var retrieved = new HashSet<String>();
        BiScrollable<AgentMessage> response = null;
        while (true) {
            response = sessionStore.readMessages(sessionId,
                                                 10,
                                                 false,
                                                 AgentUtils.getIfNotNull(response, BiScrollable::getPointer, null),
                                                 QueryDirection.OLDER);
            assertNotNull(response.getItems());
            if (response.getItems().isEmpty()) break;
            response.getItems().forEach(m -> retrieved.add(m.getMessageId()));
            if (response.getPointer() == null || response.getPointer().getOlder() == null) break;
        }

        assertEquals(expectedIds.size(), retrieved.size());
        assertTrue(retrieved.containsAll(expectedIds));

        final var responseSkipSystem = sessionStore.readMessages(sessionId, 100, false, null, QueryDirection.OLDER);
        assertTrue(responseSkipSystem.getItems().stream()
                .anyMatch(m -> m.getMessageType() == AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE));
        final var skipResponse = sessionStore.readMessages(sessionId, 100, true, null, QueryDirection.OLDER);
        assertFalse(skipResponse.getItems().stream()
                .anyMatch(m -> m.getMessageType() == AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE));
        assertEquals(responseSkipSystem.getItems().size() - skipResponse.getItems().size(),
                     responseSkipSystem.getItems().stream()
                             .filter(m -> m.getMessageType() == AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE)
                             .count());
    }

    @Test
    @SneakyThrows
    void testSessionStorage() {
        final var sessionId = "test-session";
        final var sessionSummary = SessionSummary.builder()
                .sessionId(sessionId)
                .summary("Test Summary")
                .keywords(List.of("topic1", "topic2"))
                .updatedAt(AgentUtils.epochMicro())
                .build();

        final var savedSession = sessionStore.saveSession(sessionSummary);
        assertTrue(savedSession.isPresent());
        assertEquals(sessionId, savedSession.get().getSessionId());

        final var retrievedSession = sessionStore.session(sessionId);
        assertTrue(retrievedSession.isPresent());
        assertEquals("Test Summary", retrievedSession.get().getSummary());

        final var sessions = sessionStore.sessions(10, null, QueryDirection.OLDER);
        assertFalse(sessions.getItems().isEmpty());
        assertEquals(1, sessions.getItems().size());
        assertEquals(sessionId, sessions.getItems().get(0).getSessionId());

        assertTrue(sessionStore.deleteSession(sessionId));
        assertFalse(sessionStore.session(sessionId).isPresent());
    }

    @Test
    @SneakyThrows
    void testSessionsImplementation() {
        // FileSystemSessionStore returns sessions sorted by updatedAt
        // For QueryDirection.OLDER, it should be descending (newest first)
        IntStream.rangeClosed(1, 5).forEach(i -> {
            sessionStore.saveSession(SessionSummary.builder()
                    .sessionId("S-" + i)
                    .updatedAt(i * 1000L)
                    .build());
        });

        final var sessions = sessionStore.sessions(10, null, QueryDirection.OLDER);
        assertEquals(5, sessions.getItems().size());
        // Verify sorting (Descending for OLDER)
        for (int i = 0; i < 4; i++) {
            assertTrue(sessions.getItems().get(i).getUpdatedAt() >= sessions.getItems().get(i + 1).getUpdatedAt());
        }

        final var sessionsNewer = sessionStore.sessions(10, null, QueryDirection.NEWER);
        assertEquals(5, sessionsNewer.getItems().size());
        // Verify sorting (Ascending for NEWER)
        for (int i = 0; i < 4; i++) {
            assertTrue(sessionsNewer.getItems().get(i).getUpdatedAt() <= sessionsNewer.getItems().get(i + 1)
                    .getUpdatedAt());
        }
    }

    @Test
    @SneakyThrows
    void testSessionsPagination() {
        IntStream.rangeClosed(1, 10).forEach(i -> {
            sessionStore.saveSession(SessionSummary.builder()
                    .sessionId("S-" + i)
                    .updatedAt(i * 1000L)
                    .build());
        });

        // Fetch first 3 (OLDER, so newest first: S-10, S-9, S-8)
        var response = sessionStore.sessions(3, null, QueryDirection.OLDER);
        assertEquals(3, response.getItems().size());
        assertEquals("S-10", response.getItems().get(0).getSessionId());
        assertEquals("S-9", response.getItems().get(1).getSessionId());
        assertEquals("S-8", response.getItems().get(2).getSessionId());
        assertNotNull(response.getPointer().getOlder());

        // Fetch next 3
        response = sessionStore.sessions(3, response.getPointer().getOlder(), QueryDirection.OLDER);
        assertEquals(3, response.getItems().size());
        assertEquals("S-7", response.getItems().get(0).getSessionId());
        assertEquals("S-6", response.getItems().get(1).getSessionId());
        assertEquals("S-5", response.getItems().get(2).getSessionId());

        // Fetch remaining
        response = sessionStore.sessions(10, response.getPointer().getOlder(), QueryDirection.OLDER);
        assertEquals(4, response.getItems().size());
        assertEquals("S-4", response.getItems().get(0).getSessionId());
        assertEquals("S-1", response.getItems().get(3).getSessionId());
    }
}
