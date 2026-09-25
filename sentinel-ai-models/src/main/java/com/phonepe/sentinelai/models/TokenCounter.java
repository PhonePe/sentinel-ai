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

package com.phonepe.sentinelai.models;

import com.knuddels.jtokkit.api.EncodingType;

import com.phonepe.sentinelai.core.agentmessages.AgentMessage;

import java.util.List;

/**
 * Interface for counting tokens in messages.
 * By default, Sentinel AI uses OpenAICompletionsTokenCounter for OpenAI models.
 * However certain proviers like anthropic have an endpoint thar can provide precise counts. Override that if needed.
 */
public interface TokenCounter {
    /**
     * Count tokens in the given text
     *
     * @param messages     Messages to count tokens in
     * @param config       Configuration for token counting
     * @param encodingType Encoding type to use for token counting
     * @return Number of tokens in the messages
     */
    int estimateTokenCount(final List<AgentMessage> messages,
                           final TokenCountingConfig config,
                           final EncodingType encodingType);

}
