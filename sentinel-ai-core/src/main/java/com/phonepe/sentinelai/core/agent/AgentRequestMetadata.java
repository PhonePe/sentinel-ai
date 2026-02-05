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

package com.phonepe.sentinelai.core.agent;

import com.phonepe.sentinelai.core.model.ModelUsageStats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Optional metadata that can be sent to agent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRequestMetadata {
    /**
     * Session ID for the current conversation. This is passed to LLM as additional data in system prompt.
     */
    private String sessionId;
    /**
     * A User ID for the user the agent is having the current conversation with. This is passed to LLM as additional
     * data in system prompt.
     */
    private String userId;

    /**
     * Any other custom parameters that need to be passed to the agent or the tools being invoked by the agent. This is
     * passed to LLM as additional data in system prompt.
     */
    private Map<String, Object> customParams;

    /**
     * Global usage stats object that can be used to track usage of the model across execute calls.
     */
    private ModelUsageStats usageStats;
}
