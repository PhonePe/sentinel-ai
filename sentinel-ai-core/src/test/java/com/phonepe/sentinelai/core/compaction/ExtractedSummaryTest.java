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

package com.phonepe.sentinelai.core.compaction;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExtractedSummaryTest {

    @Test
    void testBuilder() {
        final var summary = ExtractedSummary.builder()
                .title("Test Title")
                .summary("Test summary content")
                .keywords(List.of("keyword1", "keyword2"))
                .rawData(null)
                .build();

        assertEquals("Test Title", summary.getTitle());
        assertEquals("Test summary content", summary.getSummary());
        assertEquals(List.of("keyword1", "keyword2"), summary.getKeywords());
        assertNull(summary.getRawData());
    }

    @Test
    void testBuilderWithRawData() throws Exception {
        final var mapper = new ObjectMapper();
        final var rawData = mapper.readTree("{\"key\": \"value\"}");

        final var summary = ExtractedSummary.builder()
                .title("Title")
                .summary("Summary")
                .keywords(List.of("key"))
                .rawData(rawData)
                .build();

        assertNotNull(summary.getRawData());
        assertEquals("value", summary.getRawData().get("key").asText());
    }

    @Test
    void testFieldNames() {
        assertEquals("summary", ExtractedSummary.Fields.summary);
        assertEquals("title", ExtractedSummary.Fields.title);
        assertEquals("keywords", ExtractedSummary.Fields.keywords);
        assertEquals("rawData", ExtractedSummary.Fields.rawData);
    }
}
