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

package com.phonepe.sentinelai.storage.session;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.AgentMessageType;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.utils.AgentUtils;
import com.phonepe.sentinelai.core.utils.TestUtils;
import com.phonepe.sentinelai.session.BiScrollable;
import com.phonepe.sentinelai.session.QueryDirection;
import com.phonepe.sentinelai.session.SessionSummary;
import com.phonepe.sentinelai.storage.ESClient;
import com.phonepe.sentinelai.storage.ESIntegrationTestBase;
import com.phonepe.sentinelai.storage.IndexSettings;

import lombok.SneakyThrows;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ESSessionStoreTest extends ESIntegrationTestBase {

    @Test
    @SneakyThrows
    void testSessionMessageStorage() {
        try (final var client = ESClient.builder()
                .serverUrl(ELASTICSEARCH_CONTAINER.getHttpHostAddress())
                .apiKey(TestUtils.getTestProperty("ES_API_KEY", "test"))
                .build()) {

            final var sessionStore = ESSessionStore.builder()
                    .client(client)
                    .indexPrefix("test-msg")
                    .sessionIndexSettings(IndexSettings.DEFAULT)
                    .messageIndexSettings(IndexSettings.DEFAULT)
                    .build();

            final var sessionId = "msg-session";
            final var runId = UUID.randomUUID().toString();

            final var messages = IntStream.rangeClosed(1, 50).mapToObj(i -> {
                if (i % 5 == 0) {
                    return ToolCall.builder()
                            .sessionId(sessionId)
                            .runId(runId)
                            .toolCallId("tc-" + i)
                            .toolName("echo")
                            .arguments("{\"i\":" + i + "}")
                            .build();
                }
                if (i % 4 == 0) {
                    return ToolCallResponse.builder()
                            .sessionId(sessionId)
                            .runId(runId)
                            .toolCallId("tcr-" + i)
                            .toolName("echo")
                            .errorType(ErrorType.SUCCESS)
                            .response("response-" + i)
                            .build();
                }
                if (i % 3 == 0) {
                    return SystemPrompt.builder()
                            .sessionId(sessionId)
                            .runId(runId)
                            .content("system-" + i)
                            .dynamic(false)
                            .methodReference(null)
                            .build();
                }
                if (i % 2 == 0) {
                    return UserPrompt.builder()
                            .sessionId(sessionId)
                            .runId(runId)
                            .content("user-" + i)
                            .sentAt(null)
                            .build();
                }
                return Text.builder().sessionId(sessionId).runId(runId).content("text-" + i).build();
            }).toList();

            final var expectedIds = messages.stream()
                    .map(AgentMessage::getMessageId)
                    .collect(Collectors.toUnmodifiableSet());

            sessionStore.saveMessages(sessionId, runId, messages);

            String nextPointer = null;
            String prevPointer;
            final var retrieved = new HashSet<String>();
            final var maxIterations = 100;
            var iter = 0;
            BiScrollable<AgentMessage> response = null;
            while (iter++ < maxIterations) {
                response = sessionStore.readMessages(sessionId, 10, false, AgentUtils.getIfNotNull(response,
                        BiScrollable::getPointer, null), QueryDirection.OLDER);
                assertNotNull(response.getItems());
                response.getItems().forEach(m -> retrieved.add(m.getMessageId()));
                if (retrieved.containsAll(expectedIds)) {
                    break;
                }
                prevPointer = nextPointer;
                assertNotNull(response.getPointer());
                nextPointer = response.getPointer().getOlder();
                if (Strings.isNullOrEmpty(nextPointer) || nextPointer.equals(prevPointer)) {
                    break;
                }
            }

            assertEquals(expectedIds.size(), retrieved.size());
            assertTrue(retrieved.containsAll(expectedIds),
                    () -> "Retrieved messages do not contain all saved messages. Missing: " + String.join(",", Sets
                            .difference(expectedIds, retrieved)));

            final var responseSkipSystem = sessionStore.readMessages(sessionId, 100, true, null, QueryDirection.OLDER);
            assertNotNull(responseSkipSystem.getItems());
            final var anySystem = responseSkipSystem.getItems()
                    .stream()
                    .anyMatch(m -> m.getMessageType().equals(AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE));
            assertFalse(anySystem);

            final var responseNewer = sessionStore.readMessages(sessionId, 10, true, null, QueryDirection.NEWER);
            assertNotNull(responseNewer.getItems());
            assertFalse(responseNewer.getItems().isEmpty());
            assertTrue(responseNewer.getItems().get(0).getTimestamp() <= responseNewer.getItems()
                    .get(1)
                    .getTimestamp());
            assertNotNull(responseNewer.getPointer());
            assertNotNull(responseNewer.getPointer().getNewer());

            final var secondBatchNewer = sessionStore.readMessages(sessionId, 10, true, responseNewer.getPointer(),
                    QueryDirection.NEWER);
            assertNotNull(secondBatchNewer.getItems());
            assertFalse(secondBatchNewer.getItems().isEmpty());
            assertTrue(secondBatchNewer.getItems().get(0).getTimestamp() >= responseNewer.getItems()
                    .get(responseNewer.getItems().size() - 1)
                    .getTimestamp());
        }
    }

    @Test
    @SneakyThrows
    void testSessionStorage() {
        try (final var client = ESClient.builder()
                .serverUrl(ELASTICSEARCH_CONTAINER.getHttpHostAddress())
                .apiKey(TestUtils.getTestProperty("ES_API_KEY", "test"))
                .build()) {

            final var sessionStore = ESSessionStore.builder()
                    .client(client)
                    .indexPrefix("test")
                    .sessionIndexSettings(IndexSettings.DEFAULT)
                    .messageIndexSettings(IndexSettings.DEFAULT)
                    .build();


            final var sessionId = "test-session";
            final var agentName = "test-agent";
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

            final var updatedSessionSummary = SessionSummary.builder()
                    .sessionId(sessionId)
                    .summary("Updated Summary")
                    .keywords(List.of("topic1", "topic2"))
                    .updatedAt(AgentUtils.epochMicro())
                    .build();
            final var updatedSession = sessionStore.saveSession(updatedSessionSummary);
            assertTrue(updatedSession.isPresent());
            assertEquals("Updated Summary", updatedSession.get().getSummary());
            assertTrue(sessionStore.deleteSession(sessionId));
            assertFalse(sessionStore.session(sessionId).isPresent());

            final var savedIds = IntStream.rangeClosed(1, 25)
                    .mapToObj(i -> sessionStore.saveSession(SessionSummary.builder()
                            .sessionId("S-" + i)
                            .summary("Summary " + i)
                            .keywords(List.of())
                            .updatedAt(AgentUtils.epochMicro())
                            .build()).map(SessionSummary::getSessionId).orElse(null))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toUnmodifiableSet());
            var nextPointer = "";
            final var retrieved = new HashSet<String>();
            do {
                final var response = sessionStore.sessions(10, nextPointer, QueryDirection.OLDER);
                assertNotNull(response.getItems());
                response.getItems().forEach(s -> retrieved.add(s.getSessionId()));
                assertNotNull(response.getPointer());
                nextPointer = response.getPointer().getOlder();
            } while (!retrieved.containsAll(savedIds));
            assertEquals(savedIds.size(), retrieved.size(), () -> {
                final var savedSize = savedIds.size();
                final var retrievedSize = retrieved.size();
                final var diff = savedSize > retrievedSize ? Sets.difference(savedIds, retrieved) : Sets.difference(
                        retrieved, savedIds);
                return "Expected to retrieve %d sessions, but got %d. Extra: %s".formatted(savedSize, retrievedSize,
                        String.join(",", diff));
            });
            assertTrue(retrieved.containsAll(savedIds),
                    () -> "Retrieved sessions do not contain all saved sessions. Missing: " + String.join(",", Sets
                            .difference(savedIds, retrieved)));
        }
    }
}
