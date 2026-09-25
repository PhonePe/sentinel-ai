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

package com.phonepe.sentinelai.models.wire;

/**
 * Normalized token usage of a single model call. All counts are optional; a protocol sets a
 * count only when the provider reports it.
 *
 * @param inputTokens           Tokens consumed by the input/prompt.
 * @param outputTokens          Tokens produced by the output/completion.
 * @param totalTokens           Total tokens for the call.
 * @param inputAudioTokens      Audio tokens in the input (provider detail).
 * @param inputCachedTokens     Cached tokens in the input (provider detail).
 * @param outputAudioTokens     Audio tokens in the output (provider detail).
 * @param outputReasoningTokens Reasoning tokens in the output (provider detail).
 */
public record WireUsage(
        Integer inputTokens,
        Integer outputTokens,
        Integer totalTokens,
        Integer inputAudioTokens,
        Integer inputCachedTokens,
        Integer outputAudioTokens,
        Integer outputReasoningTokens
) {

    /**
     * @return The given value, or zero when the provider did not report it.
     */
    public static int orZero(final Integer value) {
        return value == null ? 0 : value;
    }
}
