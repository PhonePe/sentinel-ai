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

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * A summary for the session
 */
@Value
@JsonClassDescription("A summary for the session based on the latest few messages")
@Builder
public class SessionSummary {
    @JsonPropertyDescription("Session id for this session")
    String sessionId;

    String title;

    @JsonPropertyDescription("A short summary of the conversation thus far between the user and the agent")
    String summary;

    @JsonPropertyDescription("A short list of topics being discussed")
    List<String> keywords;

    @JsonPropertyDescription("Message ID till which summarization has already been done")
    String lastSummarizedMessageId;

    @JsonPropertyDescription("Structured data extracted from the conversation")
    String raw;

    long updatedAt;
}
