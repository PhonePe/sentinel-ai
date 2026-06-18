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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionSummaryTest {

    @Test
    void testExtraData() {
        final var extraData = Map.<String, Object>of(
                                                     "key1",
                                                     "value1",
                                                     "key2",
                                                     42,
                                                     "key3",
                                                     true);
        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Title")
                .summary("Summary")
                .extra(extraData)
                .updatedAt(System.currentTimeMillis())
                .build();

        assertEquals(extraData, sessionSummary.getExtra());
        assertEquals(3, sessionSummary.getExtra().size());
        assertEquals("value1", sessionSummary.getExtra().get("key1"));
        assertEquals(42, sessionSummary.getExtra().get("key2"));
        assertEquals(true, sessionSummary.getExtra().get("key3"));
    }

    @Test
    void testExtraFieldDefaultsToEmptyMap() {
        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Title")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        assertNotNull(sessionSummary.getExtra());
        assertTrue(sessionSummary.getExtra().isEmpty());
    }

    @Test
    void testExtraMethodModifiesExtra() {
        final var originalSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Title")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var newExtra = Map.<String, Object>of("newKey", "newValue");
        final var modifiedSummary = originalSummary.withExtra(newExtra);

        assertTrue(originalSummary.getExtra().isEmpty());
        assertEquals(newExtra, modifiedSummary.getExtra());
        assertEquals("newValue", modifiedSummary.getExtra().get("newKey"));
    }

    @Test
    void testExtraPreservesOtherFields() {
        final var sessionId = "s1";
        final var title = "Title";
        final var summary = "Summary";
        long updatedAt = System.currentTimeMillis();

        final var originalSummary = SessionSummary.builder()
                .sessionId(sessionId)
                .title(title)
                .summary(summary)
                .updatedAt(updatedAt)
                .build();

        final var newExtra = Map.<String, Object>of("key", "value");
        final var modifiedSummary = originalSummary.withExtra(newExtra);

        assertEquals(sessionId, modifiedSummary.getSessionId());
        assertEquals(title, modifiedSummary.getTitle());
        assertEquals(summary, modifiedSummary.getSummary());
        assertEquals(updatedAt, modifiedSummary.getUpdatedAt());
        assertEquals(newExtra, modifiedSummary.getExtra());
    }

    @Test
    void testSessionSummaryBuilderAndGetters() {
        String sessionId = "s1";
        String title = "Session Title";
        String summary = "This is a summary";
        List<String> keywords = List.of("topic1", "topic2");
        String lastMsgId = "msg-100";
        String raw = "{\"data\": \"value\"}";
        long updatedAt = System.currentTimeMillis();

        SessionSummary sessionSummary = SessionSummary.builder()
                .sessionId(sessionId)
                .title(title)
                .summary(summary)
                .keywords(keywords)
                .lastSummarizedMessageId(lastMsgId)
                .raw(raw)
                .updatedAt(updatedAt)
                .build();

        assertEquals(sessionId, sessionSummary.getSessionId());
        assertEquals(title, sessionSummary.getTitle());
        assertEquals(summary, sessionSummary.getSummary());
        assertEquals(keywords, sessionSummary.getKeywords());
        assertEquals(lastMsgId, sessionSummary.getLastSummarizedMessageId());
        assertEquals(raw, sessionSummary.getRaw());
        assertEquals(updatedAt, sessionSummary.getUpdatedAt());
    }

    @Test
    void testUpdatedAtPreservesExtraData() {
        final var extraData = Map.<String, Object>of("key", "value");
        final var originalSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Title")
                .summary("Summary")
                .extra(extraData)
                .updatedAt(100L)
                .build();

        final var modifiedSummary = originalSummary.withUpdatedAt(200L);

        assertEquals(extraData, modifiedSummary.getExtra());
        assertEquals(200L, modifiedSummary.getUpdatedAt());
    }
}
