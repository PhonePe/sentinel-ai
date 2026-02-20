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

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionSummaryTest {

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
}
