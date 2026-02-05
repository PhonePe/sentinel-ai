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

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import lombok.Value;

import java.util.List;

/**
 * Summary extracted by LLM
 */
@Value
@JsonClassDescription("Summary of the session till now and the current run")
public class ExtractedSummary {

    @JsonPropertyDescription("""
            A summary of the conversation thus far between the user and the agent. \
            Formatted in a structured manner so that it can be used by an LLM to understand the conversation \
            history thus far without needing all the raw messages""")
    String sessionSummary;

    @JsonPropertyDescription("A short title for the session summarizing the main topic being discussed")
    String title;

    @JsonPropertyDescription("Important one-word keywords/topics being discussed in the session")
    List<String> keywords;
}
