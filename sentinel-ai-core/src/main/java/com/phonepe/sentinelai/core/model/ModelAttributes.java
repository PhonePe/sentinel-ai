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

package com.phonepe.sentinelai.core.model;

import com.knuddels.jtokkit.api.EncodingType;

import lombok.Builder;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class ModelAttributes {

    public static final int DEFAULT_WINDOW_SIZE = 128_000;
    public static final EncodingType DEFAULT_ENCODING_TYPE = EncodingType.CL100K_BASE;

    public static final ModelAttributes DEFAULT_MODEL_ATTRIBUTES = new ModelAttributes(DEFAULT_WINDOW_SIZE,
                                                                                       DEFAULT_ENCODING_TYPE);

    /**
     * Size of the context window for the model
     */
    @Builder.Default
    int contextWindowSize = DEFAULT_WINDOW_SIZE;

    /**
     * Encoding used for token counting
     */
    @Builder.Default
    EncodingType encodingType = DEFAULT_ENCODING_TYPE;

    public static ModelAttributes merge(final ModelAttributes lhs, final ModelAttributes rhs) {
        if (lhs == null && rhs == null) {
            return DEFAULT_MODEL_ATTRIBUTES;
        }
        if (lhs == null) {
            return rhs;
        }
        if (rhs == null) {
            return lhs;
        }
        return new ModelAttributes(
                                   lhs.getContextWindowSize() != DEFAULT_WINDOW_SIZE
                                           ? lhs.getContextWindowSize()
                                           : rhs.getContextWindowSize(),
                                   lhs.getEncodingType() != DEFAULT_ENCODING_TYPE
                                           ? lhs.getEncodingType()
                                           : rhs.getEncodingType());
    }

}
