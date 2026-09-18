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

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class TokenCountingConfig {

    public static final int DEFAULT_IMAGE_TOKEN_COST = 765;

    public static final TokenCountingConfig DEFAULT = TokenCountingConfig
            .builder()
            .messageOverHead(3)
            .nameOverhead(1)
            .assistantPrimingOverhead(3)
            .formattingOverhead(10)
            .build();

    /**
     * Overhead per message in tokens
     */
    int messageOverHead;

    /**
     * Overhead per name in tokens
     */
    int nameOverhead;

    /**
     * Overhead for system priming in tokens. Once every message
     */
    int assistantPrimingOverhead;

    /**
     * Overhead for formatting in tokens.
     * Once every message. Used for structued arguments to tool calls etc.
     */
    int formattingOverhead;

    /**
     * Fixed token cost per image content part. Vision models do not tokenize the
     * base64 payload;
     * they count a patch grid derived from image resolution. Without this, the
     * estimator counts
     * every base64 character as text and massively overcounts image messages.
     * The default is a conservative high-detail worst case (OpenAI-style ~765
     * tokens).
     */
    @Builder.Default
    int imageTokenCost = DEFAULT_IMAGE_TOKEN_COST;
}
