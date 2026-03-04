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

import com.phonepe.sentinelai.core.compaction.CompactionPrompts;
import com.phonepe.sentinelai.core.events.EventType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AgentSessionExtensionSetupTest {

    @Test
    void testBuilder() {
        AgentSessionExtensionSetup setup = AgentSessionExtensionSetup.builder()
                .historicalMessageFetchSize(10)
                .maxSummaryLength(500)
                .autoSummarizationThresholdPercentage(50)
                .compactionTriggeringEvents(Set.of(EventType.MESSAGE_RECEIVED))
                .build();

        assertEquals(10, setup.getHistoricalMessageFetchSize());
        assertEquals(500, setup.getMaxSummaryLength());
        assertEquals(50, setup.getAutoSummarizationThresholdPercentage());
        assertEquals(Set.of(EventType.MESSAGE_RECEIVED), setup.getCompactionTriggeringEvents());
        assertEquals(CompactionPrompts.DEFAULT, setup.getCompactionPrompts());
    }

    @Test
    void testDefaultValues() {
        AgentSessionExtensionSetup setup = AgentSessionExtensionSetup.DEFAULT;
        assertEquals(AgentSessionExtensionSetup.MAX_HISTORICAL_MESSAGES_FETCH_COUNT,
                     setup.getHistoricalMessageFetchSize());
        assertEquals(AgentSessionExtensionSetup.DEFAULT_MAX_SUMMARY_LENGTH, setup.getMaxSummaryLength());
        assertEquals(AgentSessionExtensionSetup.DEFAULT_AUTOMATIC_SUMMARIZATION_THRESHOLD,
                     setup.getAutoSummarizationThresholdPercentage());
        assertEquals(CompactionPrompts.DEFAULT, setup.getCompactionPrompts());
        assertEquals(AgentSessionExtensionSetup.DEFAULT_COMPACTION_TRIGGERING_EVENTS,
                     setup.getCompactionTriggeringEvents());
    }

    @Test
    void testWithers() {
        AgentSessionExtensionSetup setup = AgentSessionExtensionSetup.DEFAULT
                .withMaxSummaryLength(2000)
                .withHistoricalMessageFetchSize(20);

        assertEquals(2000, setup.getMaxSummaryLength());
        assertEquals(20, setup.getHistoricalMessageFetchSize());
        assertNotEquals(AgentSessionExtensionSetup.DEFAULT.getMaxSummaryLength(), setup.getMaxSummaryLength());
    }
}
