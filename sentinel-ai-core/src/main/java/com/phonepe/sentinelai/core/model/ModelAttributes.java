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

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.EncodingType;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

@Value
@Builder
@With
public class ModelAttributes {

    public static final ModelAttributes DEFAULT_MODEL_ATTRIBUTES = ModelAttributes.builder()
            .contextWindowSize(128_000)
            .encodingType(EncodingType.CL100K_BASE)
            .build();

    /**
     * Size of the context window for the model
     */
    int contextWindowSize;

    /**
     * Encoding used for token counting
     */
    @Builder.Default
    EncodingType encodingType = EncodingType.CL100K_BASE;

}
