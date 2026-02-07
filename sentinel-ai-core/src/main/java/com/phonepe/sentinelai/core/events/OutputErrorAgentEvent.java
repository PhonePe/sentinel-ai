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

package com.phonepe.sentinelai.core.events;

import com.phonepe.sentinelai.core.errors.ErrorType;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.time.Duration;

/**
 * There was an error in the model call
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class OutputErrorAgentEvent extends AgentEvent {
    /**
     * Error type in case the output generation resulted in an error.
     */
    ErrorType errorType;

    /**
     * Serialized content for structured output
     */
    String content;
    /**
     * Elapsed time taken to generate the final output
     */
    Duration elapsedTime;

    @Builder
    @Jacksonized
    public OutputErrorAgentEvent(@NonNull String agentName,
                                 @NonNull String runId,
                                 String sessionId,
                                 String userId,
                                 ErrorType errorType,
                                 @NonNull String content,
                                 @NonNull Duration elapsedTime) {
        super(EventType.OUTPUT_ERROR, agentName, runId, sessionId, userId);
        this.errorType = errorType;
        this.content = content;
        this.elapsedTime = elapsedTime;
    }

    @Override
    public <T> T accept(AgentEventVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
