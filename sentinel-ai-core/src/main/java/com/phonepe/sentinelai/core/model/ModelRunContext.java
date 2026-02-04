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

package com.phonepe.sentinelai.core.model;

import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import lombok.Value;

/**
 * A context object passed to the model at runtime.
 */
@Value
public class ModelRunContext {
    /**
     * Name of the agent that is running this model
     */
    String agentName;
    /**
     * An id for this particular run. This is used to track the run in logs and events
     */
    String runId;

    /**
     * Session id for this run. This is used to track the run in logs and events
     */
    String sessionId;

    /**
     * User id for this run. This is used to track the run in logs and events
     */
    String userId;

    /**
     * Required setup for the agent
     */
    AgentSetup agentSetup;

    /**
     * Model usage stats for this run
     */
    ModelUsageStats modelUsageStats;

    /**
     * Processing mode for this run
     */
    ProcessingMode processingMode;
}
