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


import lombok.Builder;
import lombok.Value;
import lombok.With;

/**
 * Setup for Agent Session Extension
 */
@Value
@With
@Builder
@SuppressWarnings("java:S6548")
public class AgentSessionExtensionSetup {
    public static final int DEFAULT_MAX_HISTORICAL_MESSAGES_FETCH_COUNT = 30;
    public static final boolean DEFAULT_PRE_SUMMARIZATION_DISABLED = false;

    public static final AgentSessionExtensionSetup DEFAULT = new AgentSessionExtensionSetup(DEFAULT_MAX_HISTORICAL_MESSAGES_FETCH_COUNT,
                                                                                            DEFAULT_PRE_SUMMARIZATION_DISABLED);

    /**
     * Number of historical messages to fetch from session store in one go.
     * <p>
     * The extension may need to fetch more messages than historicalMessagesFetchCount to find
     * all messages that need to be summarized. So it will fetch messages in batches of this size until
     * it has enough messages to summarize or there are no more messages left.
     */
    @Builder.Default
    int historicalMessageFetchSize = DEFAULT_MAX_HISTORICAL_MESSAGES_FETCH_COUNT;

    @Builder.Default
    boolean preSummarizationDisabled = DEFAULT_PRE_SUMMARIZATION_DISABLED;
}
