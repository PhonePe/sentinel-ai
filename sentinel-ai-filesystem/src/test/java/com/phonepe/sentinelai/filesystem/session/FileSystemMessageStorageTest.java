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
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.session.QueryDirection;

import lombok.SneakyThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemMessageStorageTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private FileSystemMessageStorage messageStorage;
    private String sessionDir;

    @BeforeEach
    void setUp() {
        objectMapper = JsonUtils.createMapper();
        sessionDir = tempDir.resolve("messages").toString();
        messageStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
    }

    @Test
    @SneakyThrows
    void testAddAndReadMessages() {
        final var sessionId = "test-session";
        final var runId = UUID.randomUUID().toString();
        final var messages = List.<AgentMessage>of(
                                                   Text.builder().sessionId(sessionId).runId(runId).content("msg1")
                                                           .timestamp(1000L).stats(new ModelUsageStats()).build(),
                                                   Text.builder().sessionId(sessionId).runId(runId).content("msg2")
                                                           .timestamp(2000L).stats(new ModelUsageStats()).build()
        );

        messageStorage.addMessages(messages);

        final var response = messageStorage.readMessages(10, false, null, QueryDirection.NEWER);
        assertEquals(2, response.getItems().size());
        assertEquals("msg1", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg2", ((Text) response.getItems().get(1)).getContent());
    }

    @Test
    @SneakyThrows
    void testEmptyStorage() {
        final var response = messageStorage.readMessages(10, false, null, QueryDirection.NEWER);
        assertTrue(response.getItems().isEmpty());
        assertTrue(response.getPointer().getOlder() == null || response.getPointer().getOlder().isEmpty());
        assertTrue(response.getPointer().getNewer() == null || response.getPointer().getNewer().isEmpty());
    }

    @Test
    @SneakyThrows
    void testPagination() {
        final var sessionId = "test-session";
        final var runId = UUID.randomUUID().toString();
        final var messages = IntStream.rangeClosed(1, 10).mapToObj(i -> Text.builder().sessionId(sessionId).runId(runId)
                .content("msg" + i).timestamp(i * 1000L).stats(new ModelUsageStats()).build()
        ).map(m -> (AgentMessage) m).toList();

        messageStorage.addMessages(messages);

        // Read last 3
        var response = messageStorage.readMessages(3, false, null, QueryDirection.OLDER);
        assertEquals(3, response.getItems().size());
        assertEquals("msg8", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg10", ((Text) response.getItems().get(2)).getContent());
        assertNotNull(response.getPointer().getOlder());

        // Read previous 3
        response = messageStorage.readMessages(3, false, response.getPointer(), QueryDirection.OLDER);
        assertEquals(3, response.getItems().size());
        assertEquals("msg5", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg7", ((Text) response.getItems().get(2)).getContent());

        // Read newer from null
        response = messageStorage.readMessages(3, false, null, QueryDirection.NEWER);
        assertEquals(3, response.getItems().size());
        assertEquals("msg1", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg3", ((Text) response.getItems().get(2)).getContent());
    }

    @Test
    @SneakyThrows
    void testPersistence() {
        final var sessionId = "test-session";
        final var runId = UUID.randomUUID().toString();
        final var messages = List.<AgentMessage>of(
                                                   Text.builder().sessionId(sessionId).runId(runId).content("msg1")
                                                           .timestamp(1000L).stats(new ModelUsageStats()).build()
        );

        messageStorage.addMessages(messages);

        // Create a new storage instance pointing to same directory
        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        final var response = newStorage.readMessages(10, false, null, QueryDirection.NEWER);
        assertEquals(1, response.getItems().size());
        assertEquals("msg1", ((Text) response.getItems().get(0)).getContent());
    }

    @Test
    @SneakyThrows
    void testPurge() {
        final var sessionId = "test-session";
        messageStorage.addMessages(List.of(Text.builder().sessionId(sessionId).content("msg").stats(
                                                                                                    new ModelUsageStats())
                .build()));
        assertTrue(messageStorage.purgeMessages(sessionId));
        final var response = messageStorage.readMessages(10, false, null, QueryDirection.NEWER);
        assertTrue(response.getItems().isEmpty());
    }

    @Test
    @SneakyThrows
    void testSkipSystemPrompt() {
        final var sessionId = "test-session";
        final var messages = List.of(
                                     Text.builder().sessionId(sessionId).content("user").timestamp(1000L).stats(
                                                                                                                new ModelUsageStats())
                                             .build(),
                                     com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt.builder()
                                             .sessionId(sessionId).content("system").timestamp(2000L).build()
        );
        messageStorage.addMessages(messages);

        final var response = messageStorage.readMessages(10, true, null, QueryDirection.NEWER);
        assertEquals(1, response.getItems().size());
        assertEquals("user", ((Text) response.getItems().get(0)).getContent());
    }
}
