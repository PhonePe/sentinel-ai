package com.phonepe.sentinelai.storage.session;

import com.phonepe.sentinel.session.SessionSummary;
import com.phonepe.sentinelai.storage.ESClient;
import com.phonepe.sentinelai.storage.ESIntegrationTestBase;
import com.phonepe.sentinelai.storage.IndexSettings;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

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
                    .indexSettings(IndexSettings.DEFAULT)
                    .build();

            // Test saving a session
            final var sessionId = "test-session";
            final var agentName = "test-agent";
            final var sessionSummary = SessionSummary.builder()
                    .sessionId(sessionId)
                    .summary("Test Summary")
                    .topics(List.of("topic1", "topic2"))
                    .build();

            final var savedSession = sessionStore.saveSession(agentName, sessionSummary);
            assertTrue(savedSession.isPresent());
            assertEquals(sessionId, savedSession.get().getSessionId());

            // Test retrieving a session
            final var retrievedSession = sessionStore.session(sessionId);
            assertTrue(retrievedSession.isPresent());
            assertEquals("Test Summary", retrievedSession.get().getSummary());

            // Test retrieving all sessions
            final var sessions = sessionStore.sessions(agentName);
            assertFalse(sessions.isEmpty());
            assertEquals(1, sessions.size());
            assertEquals(sessionId, sessions.get(0).getSessionId());

            //test session summary update
            final var updatedSessionSummary = SessionSummary.builder()
                    .sessionId(sessionId)
                    .summary("Updated Summary")
                    .topics(List.of("topic1", "topic2"))
                    .build();
            //Assertions
            final var updatedSession = sessionStore.saveSession(agentName, updatedSessionSummary);
            assertTrue(updatedSession.isPresent());
            assertEquals("Updated Summary", updatedSession.get().getSummary());

            //Test scrolling by inserting and reading 100 documents
            final var savedIds = IntStream.rangeClosed(1, 100)
                    .mapToObj(i -> sessionStore.saveSession(agentName, SessionSummary.builder()
                                                                    .sessionId("S-" + i)
                                                                    .summary("Summary " + i)
                                                                    .topics(List.of())
                                                                    .build())
                            .map(SessionSummary::getSessionId)
                            .orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
            final var retrieved = sessionStore.sessions(agentName)
                    .stream()
                    .map(SessionSummary::getSessionId)
                    .collect(Collectors.toUnmodifiableSet());
            assertTrue(retrieved.containsAll(savedIds));
        }
    }
}