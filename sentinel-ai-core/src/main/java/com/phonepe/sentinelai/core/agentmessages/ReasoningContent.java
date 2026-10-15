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

package com.phonepe.sentinelai.core.agentmessages;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Reasoning content returned alongside an assistant response. It is an attribute of the
 * assistant turn, not a separate conversation message: captured from the reasoning field of a
 * Chat Completions assistant message or a reasoning output item of a Responses API response.
 * <p>
 * {@code rawItem} carries the provider-native payload verbatim (for example the full reasoning
 * item with {@code encrypted_content}, or the raw reasoning field value) so it can be replayed
 * byte-exact; {@code content} is the normalized plain text for display; {@code wireFieldName}
 * records which message field the reasoning arrived in ({@code reasoning_content}, {@code thinking}
 * etc.) so a replaying codec re-emits the original field shape.
 */
@Value
@Builder
@Jacksonized
public class ReasoningContent {

    /**
     * Normalized plain-text reasoning for display; null when only an opaque payload exists.
     */
    String content;

    /**
     * Provider-native reasoning payload exactly as received; replayed verbatim.
     */
    JsonNode rawItem;

    /**
     * Name of the message field the reasoning arrived in (Chat Completions protocols); null for
     * item-based (Responses) reasoning.
     */
    String wireFieldName;
}
