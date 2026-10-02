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
    public static final ModelOptions DEFAULT = new ModelOptions(DEFAULT_TOOL_CHOICE,
                                                                DEFAULT_TOKEN_COUNTING_CONFIG,
                                                                null);

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

    /**
     * Free-form extras merged into the request body last; extras override protocol fields.
     */
    ObjectNode extras;

    @Builder
    @Jacksonized
    public ModelOptions(final ToolChoice toolChoice,
                        final TokenCountingConfig tokenCountingConfig,
                        final ObjectNode extras) {
        this.toolChoice = Objects.requireNonNullElse(toolChoice, DEFAULT_TOOL_CHOICE);
        this.tokenCountingConfig = Objects.requireNonNullElse(tokenCountingConfig,
                                                              TokenCountingConfig.DEFAULT);
        this.extras = extras;
    }

    public ModelOptions merge(final ModelOptions other) {
        if (other == null) {
            return this;
        }
        return new ModelOptions(Objects.requireNonNullElse(other.getToolChoice(), this.toolChoice),
                                Objects.requireNonNullElse(other.getTokenCountingConfig(),
                                                           this.tokenCountingConfig),
                                Objects.requireNonNullElse(other.getExtras(), this.extras));
    }
}
