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

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import lombok.Value;
import lombok.With;

import java.util.List;

/**
 * Context injected into the agent at runtime. This remains constant for a particular agent run
 */
@Value
@With
public class AgentRunContext<R> {
    /**
     * An id for this particular run. This is used to track the run in logs and events
     */
    String runId;
    /**
     * Request
     */
    R request;

    /**
     * Metadata for the request
     */
    AgentRequestMetadata requestMetadata;

    /**
     * Required setup for the agent
     */
    AgentSetup agentSetup;

    /**
     * Old messages
     */
    List<AgentMessage> oldMessages;

    /**
     * Model usage stats for this run
     */
    ModelUsageStats modelUsageStats;

    /**
     * Processing mode for this run
     */
    ProcessingMode processingMode;
}
