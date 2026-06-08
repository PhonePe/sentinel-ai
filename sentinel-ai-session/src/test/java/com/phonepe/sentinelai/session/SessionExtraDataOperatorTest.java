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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionExtraDataOperatorTest {

    @Test
    void testCustomOperator() {
        final var operator = new SessionExtraDataOperator() {
            @Override
            protected Optional<Map<String, Object>> operate(SessionSummary sessionSummary) {
                if (sessionSummary.getSessionId().startsWith("special-")) {
                    return Optional.of(Map.of("type", "special"));
                }
                return Optional.empty();
            }
        };

        final var specialSession = SessionSummary.builder()
                .sessionId("special-123")
                .title("Test")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var resultSpecial = operator.apply(specialSession);
        assertEquals("special", resultSpecial.getExtra().get("type"));

        final var normalSession = SessionSummary.builder()
                .sessionId("normal-123")
                .title("Test")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var resultNormal = operator.apply(normalSession);
        assertTrue(resultNormal.getExtra().isEmpty());
    }

    @Test
    void testEmptyOperatorMultipleInvocations() {
        final var operator = SessionExtraDataOperator.empty();
        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Test")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result1 = operator.apply(sessionSummary);
        final var result2 = operator.apply(result1);
        final var result3 = operator.apply(result2);

        assertTrue(result1.getExtra().isEmpty());
        assertTrue(result2.getExtra().isEmpty());
        assertTrue(result3.getExtra().isEmpty());
    }

    @Test
    void testEmptyOperatorUnmodified() {
        final var operator = SessionExtraDataOperator.empty();
        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Test Session")
                .summary("Test Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = operator.apply(sessionSummary);

        assertEquals(sessionSummary.getSessionId(), result.getSessionId());
        assertEquals(sessionSummary.getExtra(), result.getExtra());
        assertTrue(result.getExtra().isEmpty());
    }

    @Test
    void testFixedOperatorComplexData() {
        final var nestedMap = Map.of("nested", "data");
        final var extraData = Map.<String, Object>of(
                                                     "stringValue",
                                                     "text",
                                                     "numberValue",
                                                     123L,
                                                     "booleanValue",
                                                     false,
                                                     "nestedData",
                                                     nestedMap
        );
        final var operator = SessionExtraDataOperator.fixed(extraData);
        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Test")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = operator.apply(sessionSummary);

        assertEquals(4, result.getExtra().size());
        assertEquals("text", result.getExtra().get("stringValue"));
        assertEquals(123L, result.getExtra().get("numberValue"));
        assertEquals(false, result.getExtra().get("booleanValue"));
        assertSame(nestedMap, result.getExtra().get("nestedData"));
    }

    @Test
    void testFixedOperatorEmptyMap() {
        final var operator = SessionExtraDataOperator.fixed(Map.of());
        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Test Session")
                .summary("Test Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = operator.apply(sessionSummary);

        assertTrue(result.getExtra().isEmpty());
    }

    @Test
    void testFixedOperatorInjectsData() {
        final var extraData = Map.<String, Object>of("key1", "value1", "key2", 42, "key3", true);
        final var operator = SessionExtraDataOperator.fixed(extraData);
        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Test Session")
                .summary("Test Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result = operator.apply(sessionSummary);

        assertEquals(sessionSummary.getSessionId(), result.getSessionId());
        assertNotNull(result.getExtra());
        assertEquals(3, result.getExtra().size());
        assertEquals("value1", result.getExtra().get("key1"));
        assertEquals(42, result.getExtra().get("key2"));
        assertEquals(true, result.getExtra().get("key3"));
    }

    @Test
    void testFixedOperatorPreservesMetadata() {
        final var extraData = Map.<String, Object>of("customKey", "customValue");
        final var operator = SessionExtraDataOperator.fixed(extraData);
        final var sessionId = "session-123";
        final var title = "Important Session";
        final var summary = "Session summary text";
        final var updatedAt = 1623456789L;
        final var keywords = List.of("topic1", "topic2");
        final var lastMsgId = "msg-999";
        final var raw = "{}";

        final var sessionSummary = SessionSummary.builder()
                .sessionId(sessionId)
                .title(title)
                .summary(summary)
                .keywords(keywords)
                .lastSummarizedMessageId(lastMsgId)
                .raw(raw)
                .updatedAt(updatedAt)
                .build();

        final var result = operator.apply(sessionSummary);

        assertEquals(sessionId, result.getSessionId());
        assertEquals(title, result.getTitle());
        assertEquals(summary, result.getSummary());
        assertEquals(keywords, result.getKeywords());
        assertEquals(lastMsgId, result.getLastSummarizedMessageId());
        assertEquals(raw, result.getRaw());
        assertEquals(updatedAt, result.getUpdatedAt());
        assertEquals(extraData, result.getExtra());
    }

    @Test
    void testOperatorChaining() {
        final var operator1 = SessionExtraDataOperator.fixed(Map.of("first", "data"));
        final var operator2 = SessionExtraDataOperator.fixed(Map.of("second", "data"));

        final var sessionSummary = SessionSummary.builder()
                .sessionId("s1")
                .title("Test")
                .summary("Summary")
                .updatedAt(System.currentTimeMillis())
                .build();

        final var result1 = operator1.apply(sessionSummary);
        final var result2 = operator2.apply(result1);

        assertEquals("data", result2.getExtra().get("second"));
        assertFalse(result2.getExtra().containsKey("first"),
                    "operator2 should replace, not merge, the extra data set by operator1");
    }

}
