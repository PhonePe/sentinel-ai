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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AgentSessionExtensionSetupTest {

    @Test
    void testBuilder() {
        AgentSessionExtensionSetup setup = AgentSessionExtensionSetup.builder()
                .historicalMessageFetchSize(10)
                .preSummarizationDisabled(true)
                .build();

        assertEquals(10, setup.getHistoricalMessageFetchSize());
        assertTrue(setup.isPreSummarizationDisabled());
    }

    @Test
    void testDefaultValues() {
        AgentSessionExtensionSetup setup = AgentSessionExtensionSetup.DEFAULT;
        assertEquals(AgentSessionExtensionSetup.DEFAULT_MAX_HISTORICAL_MESSAGES_FETCH_COUNT,
                     setup.getHistoricalMessageFetchSize());
        assertFalse(setup.isPreSummarizationDisabled());
    }

    @Test
    void testWithers() {
        AgentSessionExtensionSetup setup = AgentSessionExtensionSetup.DEFAULT
                .withHistoricalMessageFetchSize(20)
                .withPreSummarizationDisabled(true);

        assertEquals(20, setup.getHistoricalMessageFetchSize());
        assertTrue(setup.isPreSummarizationDisabled());
    }
}
