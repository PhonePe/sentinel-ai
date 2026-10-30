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
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.session.QueryDirection;

import lombok.SneakyThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
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
    void testAddMessagesInAllLoadStates() {
        writeMessages(10);

        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        final var newMessage = (AgentMessage) Text.builder().sessionId("test-session")
                .content("msg11")
                .timestamp(11_000L)
                .stats(new ModelUsageStats())
                .build();

        // Add while NOT_LOADED
        newStorage.addMessages(List.of(newMessage));

        // Tail load; the new message must be part of the latest messages
        var response = newStorage.readMessages(3, false, null, QueryDirection.OLDER);
        assertEquals(3, response.getItems().size());
        assertEquals("msg9", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg11", ((Text) response.getItems().get(2)).getContent());

        // Add while TAIL_LOADED
        final var messageAfterTail = (AgentMessage) Text.builder().sessionId("test-session")
                .content("msg12")
                .timestamp(12_000L)
                .stats(new ModelUsageStats())
                .build();
        newStorage.addMessages(List.of(messageAfterTail));
        response = newStorage.readMessages(2, false, null, QueryDirection.OLDER);
        assertEquals(2, response.getItems().size());
        assertEquals("msg12", ((Text) response.getItems().get(1)).getContent());

        // Trigger a full load, then add again
        newStorage.readMessages(100, false, null, QueryDirection.NEWER);
        final var messageAfterFull = (AgentMessage) Text.builder().sessionId("test-session")
                .content("msg13")
                .timestamp(13_000L)
                .stats(new ModelUsageStats())
                .build();
        newStorage.addMessages(List.of(messageAfterFull));
        response = newStorage.readMessages(1, false, null, QueryDirection.OLDER);
        assertEquals(1, response.getItems().size());
        assertEquals("msg13", ((Text) response.getItems().get(0)).getContent());
    }

    @Test
    @SneakyThrows
    void testCompletelyCorruptedFile() {
        // Create messages file with only corrupted content
        final var messagesFile = Path.of(sessionDir, "messages.jsonl");
        Files.createDirectories(messagesFile.getParent());
        Files.write(messagesFile,
                    List.of(
                            "not valid json",
                            "{broken",
                            "}{}{",
                            "null"
                    ),
                    StandardCharsets.UTF_8);

        // Should not crash when reading completely corrupted file
        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        final var response = newStorage.readMessages(10, false, null, QueryDirection.NEWER);

        // Should return empty result
        assertTrue(response.getItems().isEmpty());
    }

    @Test
    @SneakyThrows
    void testCorruptedJsonLine() {
        final var sessionId = "test-session";
        final var runId = UUID.randomUUID().toString();

        // Add a valid message first
        final var validMessage = Text.builder()
                .sessionId(sessionId)
                .runId(runId)
                .content("valid message")
                .timestamp(1000L)
                .stats(new ModelUsageStats())
                .build();
        messageStorage.addMessages(List.of(validMessage));

        // Now manually corrupt the messages file by appending invalid JSON
        final var messagesFile = Path.of(sessionDir, "messages.jsonl");
        final var corruptedLines = List.of(
                                           "{invalid json}",
                                           "not json at all",
                                           "{\"incomplete\": "
        );
        Files.write(messagesFile,
                    corruptedLines,
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);

        // Create new storage instance to trigger reading from file
        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);

        // Should still be able to read valid messages despite corruption
        final var response = newStorage.readMessages(10, false, null, QueryDirection.NEWER);

        // Should have the one valid message
        assertEquals(1, response.getItems().size());
        assertEquals("valid message", ((Text) response.getItems().get(0)).getContent());
    }

    @Test
    @SneakyThrows
    void testCorruptedLinesInTail() {
        writeMessages(10);

        // Append corrupted lines after the valid messages
        final var messagesFile = Path.of(sessionDir, "messages.jsonl");
        Files.write(messagesFile,
                    List.of("{invalid json}", "not json at all"),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);

        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        // The tail load must skip the corrupted lines and still serve the latest valid messages
        final var response = newStorage.readMessages(3, false, null, QueryDirection.OLDER);
        assertEquals(3, response.getItems().size());
        assertEquals("msg8", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg10", ((Text) response.getItems().get(2)).getContent());
    }

    @Test
    @SneakyThrows
    void testEmptyLinesInJsonl() {
        final var sessionId = "test-session";
        final var runId = UUID.randomUUID().toString();

        // Add a valid message
        final var message1 = Text.builder()
                .sessionId(sessionId)
                .runId(runId)
                .content("message 1")
                .timestamp(1000L)
                .stats(new ModelUsageStats())
                .build();
        messageStorage.addMessages(List.of(message1));

        // Append empty lines and another valid message
        final var messagesFile = Path.of(sessionDir, "messages.jsonl");
        Files.write(messagesFile,
                    List.of("", "  ", ""),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);

        final var message2 = Text.builder()
                .sessionId(sessionId)
                .runId(runId)
                .content("message 2")
                .timestamp(2000L)
                .stats(new ModelUsageStats())
                .build();
        messageStorage.addMessages(List.of(message2));

        // Create new storage to read from file
        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        final var response = newStorage.readMessages(10, false, null, QueryDirection.NEWER);

        // Should have both valid messages
        assertEquals(2, response.getItems().size());
        assertEquals("message 1", ((Text) response.getItems().get(0)).getContent());
        assertEquals("message 2", ((Text) response.getItems().get(1)).getContent());
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
    void testNewerQueryAfterTailLoadTriggersFullLoad() {
        writeMessages(10);

        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        // Tail load first
        newStorage.readMessages(3, false, null, QueryDirection.OLDER);
        // A NEWER query from the start must trigger a full load
        final var response = newStorage.readMessages(3, false, null, QueryDirection.NEWER);
        assertEquals(3, response.getItems().size());
        assertEquals("msg1", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg3", ((Text) response.getItems().get(2)).getContent());
    }

    @Test
    @SneakyThrows
    void testOlderPointerBelowTailTriggersFullLoad() {
        writeMessages(10);

        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        // Tail load serves the last 3 messages and gives an older pointer
        final var tailResponse = newStorage.readMessages(3, false, null, QueryDirection.OLDER);
        // Reading below the tail must trigger a full load and still work
        final var response = newStorage.readMessages(10, false, tailResponse.getPointer(), QueryDirection.OLDER);
        assertEquals(7, response.getItems().size());
        assertEquals("msg1", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg7", ((Text) response.getItems().get(6)).getContent());
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
    void testPartiallyCorruptedFile() {
        final var sessionId = "test-session";
        final var runId = UUID.randomUUID().toString();

        // Add valid messages
        final var message1 = Text.builder()
                .sessionId(sessionId)
                .runId(runId)
                .content("first valid")
                .timestamp(1000L)
                .stats(new ModelUsageStats())
                .build();

        final var message2 = Text.builder()
                .sessionId(sessionId)
                .runId(runId)
                .content("second valid")
                .timestamp(2000L)
                .stats(new ModelUsageStats())
                .build();

        messageStorage.addMessages(List.of(message1));

        // Append corrupted line
        final var messagesFile = Path.of(sessionDir, "messages.jsonl");
        Files.write(messagesFile,
                    List.of("{'malformed': json}"),
                    StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);

        // Add another valid message
        messageStorage.addMessages(List.of(message2));

        // Create new storage and read
        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        final var response = newStorage.readMessages(10, false, null, QueryDirection.NEWER);

        // Should gracefully skip the corrupted line and read valid messages
        assertEquals(2, response.getItems().size());
        assertEquals("first valid", ((Text) response.getItems().get(0)).getContent());
        assertEquals("second valid", ((Text) response.getItems().get(1)).getContent());
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
    void testPurgeAfterTailLoad() {
        writeMessages(10);

        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        newStorage.readMessages(3, false, null, QueryDirection.OLDER);
        assertTrue(newStorage.purgeMessages("test-session"));

        final var response = newStorage.readMessages(10, false, null, QueryDirection.OLDER);
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

    @Test
    @SneakyThrows
    void testSkipSystemPromptInTailLoad() {
        writeMessages(10);

        // Append system prompt messages at the end of the file
        messageStorage.addMessages(List.of(
                                           com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt.builder()
                                                   .sessionId("test-session").content("system").timestamp(20_000L)
                                                   .build(),
                                           com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt.builder()
                                                   .sessionId("test-session").content("system2").timestamp(21_000L)
                                                   .build()
        ));

        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        // The tail load must read past the two system prompt messages to serve three visible messages
        final var response = newStorage.readMessages(3, true, null, QueryDirection.OLDER);
        assertEquals(3, response.getItems().size());
        assertEquals("msg8", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg10", ((Text) response.getItems().get(2)).getContent());
        // No system prompt message must be present in the result
        response.getItems().forEach(msg -> assertNotSame(
                                                         AgentMessageType.SYSTEM_PROMPT_REQUEST_MESSAGE,
                                                         msg.getMessageType()));
    }

    @Test
    @SneakyThrows
    void testTailLoadServesLatestMessagesWithoutFullLoad() {
        writeMessages(10);

        // Fresh storage over an existing file; OLDER with no pointer must serve the latest messages from the tail
        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        final var response = newStorage.readMessages(3, false, null, QueryDirection.OLDER);
        assertEquals(3, response.getItems().size());
        assertEquals("msg8", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg9", ((Text) response.getItems().get(1)).getContent());
        assertEquals("msg10", ((Text) response.getItems().get(2)).getContent());
        assertNotNull(response.getPointer().getOlder());
    }

    @Test
    @SneakyThrows
    void testTailLoadWhenCountExceedsFileMessages() {
        writeMessages(4);

        final var newStorage = new FileSystemMessageStorage(sessionDir, objectMapper);
        // The whole file is smaller than the requested count; all messages must be returned
        final var response = newStorage.readMessages(10, false, null, QueryDirection.OLDER);
        assertEquals(4, response.getItems().size());
        assertEquals("msg1", ((Text) response.getItems().get(0)).getContent());
        assertEquals("msg4", ((Text) response.getItems().get(3)).getContent());
    }

    private void writeMessages(int count) {
        final var messages = IntStream.rangeClosed(1, count)
                .mapToObj(i -> (AgentMessage) Text.builder()
                        .sessionId("test-session")
                        .content("msg" + i)
                        .timestamp(i * 1000L)
                        .stats(new ModelUsageStats())
                        .build())
                .toList();
        messageStorage.addMessages(messages);
    }
}
