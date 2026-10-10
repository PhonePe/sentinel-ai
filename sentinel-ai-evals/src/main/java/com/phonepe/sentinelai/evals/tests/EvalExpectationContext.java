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

package com.phonepe.sentinelai.evals.tests;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.model.ModelUsageStats;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Runtime context supplied to expectations and metrics during evaluation.
 *
 * @param <R> request type associated with the evaluated agent run
 */
@Value
@Builder
public class EvalExpectationContext<R> {
    /** Unique run identifier for the current evaluation invocation. */
    String runId;
    /** Original request passed to the agent. */
    R request;
    /** Message history emitted during the agent run. */
    List<AgentMessage> oldMessages;
    /** Model usage statistics associated with the run. */
    ModelUsageStats modelUsageStats;
    /** Model name used by model-aware metric calculations. */
    String modelName;
    /** Wall-clock milliseconds for the agent {@code execute()} call only. */
    Long latencyMs;
}
