package com.phonepe.sentinelai.storage.session;

import com.google.common.collect.Sets;
import com.phonepe.sentinel.session.SessionSummary;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.storage.ESClient;
import com.phonepe.sentinelai.storage.ESIntegrationTestBase;
import com.phonepe.sentinelai.storage.IndexSettings;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ESSessionStoreTest extends ESIntegrationTestBase {

    @Test
    @SneakyThrows
    void testSessionStore() {
        try (final var client = ESClient.builder()
                .serverUrl(ELASTICSEARCH_CONTAINER.getHttpHostAddress())
                .apiKey("test")
                .build()) {

            final var sessionStore = ESSessionStore.builder()
                    .client(client)
                    .indexPrefix("test")
                    .sessionIndexSettings(IndexSettings.DEFAULT)
                    .messageIndexSettings(IndexSettings.DEFAULT)
                    .build();


            // Test saving a session
            final var sessionId = "test-session";
            final var agentName = "test-agent";
            final var sessionSummary = SessionSummary.builder()
                    .sessionId(sessionId)
                    .summary("Test Summary")
                    .keywords(List.of("topic1", "topic2"))
                    .updatedAt(AgentUtils.epochMicro())
                    .build();

            final var savedSession = sessionStore.saveSession(agentName, sessionSummary);
            assertTrue(savedSession.isPresent());
            assertEquals(sessionId, savedSession.get().getSessionId());

            // Test retrieving a session
            final var retrievedSession = sessionStore.session(sessionId);
            assertTrue(retrievedSession.isPresent());
            assertEquals("Test Summary", retrievedSession.get().getSummary());

            // Test retrieving all sessions
            final var sessions = sessionStore.sessions(10, null);
            assertFalse(sessions.getItems().isEmpty());
            assertEquals(1, sessions.getItems().size());
            assertEquals(sessionId, sessions.getItems().get(0).getSessionId());

            //test session summary update
            final var updatedSessionSummary = SessionSummary.builder()
                    .sessionId(sessionId)
                    .summary("Updated Summary")
                    .keywords(List.of("topic1", "topic2"))
                    .updatedAt(AgentUtils.epochMicro())
                    .build();
            //Assertions
            final var updatedSession = sessionStore.saveSession(agentName, updatedSessionSummary);
            assertTrue(updatedSession.isPresent());
            assertEquals("Updated Summary", updatedSession.get().getSummary());
            assertTrue(sessionStore.deleteSession(sessionId));
            assertFalse(sessionStore.session(sessionId).isPresent());

            //Test scrolling by inserting and reading 100 documents
            final var savedIds = IntStream.rangeClosed(1, 25)
                    .mapToObj(i -> sessionStore.saveSession(agentName, SessionSummary.builder()
                                    .sessionId("S-" + i)
                                    .summary("Summary " + i)
                                    .keywords(List.of())
                                    .updatedAt(AgentUtils.epochMicro())
                                    .build())
                            .map(SessionSummary::getSessionId)
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
            var nextPointer = "";
            final var retrieved = new HashSet<String>();
            do {
                final var response = sessionStore.sessions(10, nextPointer);
                response.getItems().forEach(s -> retrieved.add(s.getSessionId()));
                nextPointer = response.getNextPageToken();
            } while (!retrieved.containsAll(savedIds));
            assertEquals(savedIds.size(), retrieved.size(), () -> {
                final var savedSize = savedIds.size();
                final var retrievedSize = retrieved.size();
                final var diff = savedSize > retrievedSize
                        ? Sets.difference(savedIds, retrieved)
                        : Sets.difference(retrieved, savedIds);
                return "Expected to retrieve %d sessions, but got %d. Extra: %s".formatted(savedSize, retrievedSize, String.join(",", diff));
            });
            assertTrue(retrieved.containsAll(savedIds), () -> "Retrieved sessions do not contain all saved sessions. Missing: " + String.join(",", Sets.difference(savedIds, retrieved)));
        }
    }
}