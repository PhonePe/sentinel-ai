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

import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.Objects;

/**
 * Options for models built on the {@code sentinel-ai-models} module: tool choice resolution and
 * token counting configuration plus a free-form {@code extras} JSON node that protocols merge
 * into the request body last (extras win over protocol-built fields) to support open-weight
 * servers.
 */
@Value
public class ModelOptions {

    public static final ToolChoice DEFAULT_TOOL_CHOICE = ToolChoice.DEFAULT;
    public static final TokenCountingConfig DEFAULT_TOKEN_COUNTING_CONFIG = TokenCountingConfig.DEFAULT;
    public static final ResponseChaining DEFAULT_RESPONSE_CHAINING = ResponseChaining.OFF;
    public static final ModelOptions DEFAULT = new ModelOptions(DEFAULT_TOOL_CHOICE,
                                                                DEFAULT_TOKEN_COUNTING_CONFIG,
                                                                DEFAULT_RESPONSE_CHAINING,
                                                                null);

    /**
     * Server-side conversation chaining policy for protocols that support it (OpenAI Responses
     * API {@code previous_response_id}). Chaining stores model responses on the provider side
     * ({@code store: true}, retained there for at least 30 days) and sends only the new input
     * items per request; the provider stitches the conversation history server-side.
     */
    public enum ResponseChaining {
        /**
         * No chaining (default); every request replays the full history and nothing is stored
         * on the provider side.
         */
        OFF,
        /**
         * Chain only within a single run's tool loop; each run starts a fresh conversation.
         */
        IN_RUN,
        /**
         * Chain across runs in a session; the last stored response anchors the next request.
         * Compaction always breaks the chain.
         */
        SESSION
    }

    /**
     * Tool choice policy for the model.
     */
    public enum ToolChoice {
        /**
         * Model will always call a tool.
         */
        REQUIRED,
        /**
         * Model will call a tool only if it needs to.
         */
        AUTO,
        /**
         * REQUIRED when TOOL_BASED, AUTO when STRUCTURED_OUTPUT.
         */
        DEFAULT
    }

    ToolChoice toolChoice;

    TokenCountingConfig tokenCountingConfig;

    ResponseChaining responseChaining;

    /**
     * Free-form extras merged into the request body last; extras override protocol fields.
     */
    ObjectNode extras;

    @Builder
    @Jacksonized
    public ModelOptions(final ToolChoice toolChoice,
                        final TokenCountingConfig tokenCountingConfig,
                        final ResponseChaining responseChaining,
                        final ObjectNode extras) {
        this.toolChoice = Objects.requireNonNullElse(toolChoice, DEFAULT_TOOL_CHOICE);
        this.tokenCountingConfig = Objects.requireNonNullElse(tokenCountingConfig,
                                                              TokenCountingConfig.DEFAULT);
        this.responseChaining = Objects.requireNonNullElse(responseChaining, DEFAULT_RESPONSE_CHAINING);
        this.extras = extras;
    }

    public ModelOptions merge(final ModelOptions other) {
        if (other == null) {
            return this;
        }
        return new ModelOptions(Objects.requireNonNullElse(other.getToolChoice(), this.toolChoice),
                                Objects.requireNonNullElse(other.getTokenCountingConfig(),
                                                           this.tokenCountingConfig),
                                Objects.requireNonNullElse(other.getResponseChaining(),
                                                           this.responseChaining),
                                Objects.requireNonNullElse(other.getExtras(), this.extras));
    }
}
