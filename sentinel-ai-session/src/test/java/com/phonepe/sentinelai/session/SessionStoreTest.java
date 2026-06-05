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

package com.phonepe.sentinelai.session;


import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionStoreTest {

    private static class TestSessionStore extends SessionStore {
        private final Map<String, SessionSummary> store = new ConcurrentHashMap<>();

        TestSessionStore(SessionExtraDataOperator extraDataOperator) {
            super(extraDataOperator);
        }

        @Override
        public boolean deleteSession(String sessionId) {
            return store.remove(sessionId) != null;
        }

        @Override
        public BiScrollable<AgentMessage> readMessages(String sessionId,
                                                       int count,
                                                       boolean skipSystemPrompt,
                                                       BiScrollable.DataPointer pointer,
                                                       QueryDirection queryDirection) {
            return null;
        }

        @Override
        public void saveMessages(String sessionId,
                                 String runId,
                                 List<AgentMessage> messages) {
        }

        @Override
        public Optional<SessionSummary> session(String sessionId) {
            return Optional.ofNullable(store.get(sessionId));
        }

        @Override
        public BiScrollable<SessionSummary> sessions(int count,
                                                     String pointer,
                                                     QueryDirection queryDirection) {
            return null;
        }

        @Override
        protected Optional<SessionSummary> saveSessionImpl(SessionSummary sessionSummary) {
            store.put(sessionSummary.getSessionId(), sessionSummary);
            return session(sessionSummary.getSessionId());
        }
    }

    @Test
    void testNoExtraDataOperator() {
        final var store = new TestSessionStore(null);

        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Test")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = store.saveSession(sessionSummary);

        assertTrue(result.isPresent());
        assertTrue(result.get().getExtra().isEmpty());
    }


    @Test
    void testSaveSessionAppliesExtraData() {
        final var extraData = Map.<String, Object>of("testKey", "testValue");
        final var operator = SessionExtraDataOperator.fixed(extraData);
        final var store = new TestSessionStore(operator);

        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Test")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = store.saveSession(sessionSummary);

        assertTrue(result.isPresent());
        final var saved = result.get();
        assertEquals("s1", saved.getSessionId());
        assertEquals(extraData, saved.getExtra());
        assertEquals("testValue", saved.getExtra().get("testKey"));
    }

    @Test
    void testSaveSessionCustomOperator() {
        final var operator = new SessionExtraDataOperator() {
            @Override
            protected Optional<Map<String, Object>> operate(SessionSummary sessionSummary) {
                return Optional.of(Map.of("sessionId", sessionSummary.getSessionId()));
            }
        };
        final var store = new TestSessionStore(operator);

        final var sessionSummary = SessionSummary.builder()
                .sessionId("dynamic-id-123")
                .title("Test")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = store.saveSession(sessionSummary);

        assertTrue(result.isPresent());
        final var saved = result.get();
        assertEquals("dynamic-id-123", saved.getExtra().get("sessionId"));
    }

    @Test
    void testSaveSessionEmptyOperator() {
        final var store = new TestSessionStore(SessionExtraDataOperator.empty());

        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Test")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = store.saveSession(sessionSummary);

        assertTrue(result.isPresent());
        assertTrue(result.get().getExtra().isEmpty());
    }

    @Test
    void testSaveSessionMultipleExtraEntries() {
        final var extraData = Map.<String, Object>of(
                                                     "key1",
                                                     "value1",
                                                     "key2",
                                                     42,
                                                     "key3",
                                                     true,
                                                     "key4",
                                                     List.of("a", "b", "c")
        );
        final var operator = SessionExtraDataOperator.fixed(extraData);
        final var store = new TestSessionStore(operator);

        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Test")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = store.saveSession(sessionSummary);

        assertTrue(result.isPresent());
        final var saved = result.get();
        assertEquals(4, saved.getExtra().size());
        assertEquals("value1", saved.getExtra().get("key1"));
        assertEquals(42, saved.getExtra().get("key2"));
        assertEquals(true, saved.getExtra().get("key3"));
        assertEquals(List.of("a", "b", "c"), saved.getExtra().get("key4"));
    }

    @Test
    void testSaveSessionPreservesMetadata() {
        final var extraData = Map.<String, Object>of("extra", "data");
        final var operator = SessionExtraDataOperator.fixed(extraData);
        final var store = new TestSessionStore(operator);

        final var sessionId = "test-session-123";
        final var title = "Important Session";
        final var summary = "Session conversation summary";
        final var keywords = List.of("topic1", "topic2", "topic3");
        final var lastMsgId = "msg-999";
        final var raw = "{\"extracted\": \"data\"}";
        final var updatedAt = 1623456789L;

        final var sessionSummary = SessionSummary.builder()
                .sessionId(sessionId)
                .title(title)
                .summary(summary)
                .keywords(keywords)
                .lastSummarizedMessageId(lastMsgId)
                .raw(raw)
                .updatedAt(updatedAt)
                .build();

        final var result = store.saveSession(sessionSummary);

        assertTrue(result.isPresent());
        final var saved = result.get();
        assertEquals(sessionId, saved.getSessionId());
        assertEquals(title, saved.getTitle());
        assertEquals(summary, saved.getSummary());
        assertEquals(keywords, saved.getKeywords());
        assertEquals(lastMsgId, saved.getLastSummarizedMessageId());
        assertEquals(raw, saved.getRaw());
        assertEquals(updatedAt, saved.getUpdatedAt());
        assertEquals(extraData, saved.getExtra());
    }

}
