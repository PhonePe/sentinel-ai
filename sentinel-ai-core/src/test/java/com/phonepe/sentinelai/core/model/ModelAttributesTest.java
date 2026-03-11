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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ModelAttributesTest {

    @Test
    void mergeBothDefaultReturnsMergedDefault() {
        final var lhs = ModelAttributes.builder().build();
        final var rhs = ModelAttributes.builder().build();

        final var result = ModelAttributes.merge(lhs, rhs);

        assertEquals(ModelAttributes.DEFAULT_WINDOW_SIZE, result.getContextWindowSize());
        assertEquals(ModelAttributes.DEFAULT_ENCODING_TYPE, result.getEncodingType());
    }

    @Test
    void mergeBothNullReturnsDefault() {
        final var result = ModelAttributes.merge(null, null);
        assertSame(ModelAttributes.DEFAULT_MODEL_ATTRIBUTES, result);
    }

    @Test
    void mergeLhsNullReturnsRhs() {
        final var rhs = ModelAttributes.builder()
                .contextWindowSize(32_000)
                .encodingType(EncodingType.O200K_BASE)
                .build();
        final var result = ModelAttributes.merge(null, rhs);
        assertSame(rhs, result);
    }

    @Test
    void mergePicksLhsWhenLhsHasNonDefaultContextWindowSize() {
        final var lhs = ModelAttributes.builder()
                .contextWindowSize(32_000)
                .encodingType(EncodingType.O200K_BASE)
                .build();
        final var rhs = ModelAttributes.builder()
                .contextWindowSize(64_000)
                .encodingType(EncodingType.CL100K_BASE)
                .build();

        final var result = ModelAttributes.merge(lhs, rhs);

        // lhs is non-default, so its values should be preferred
        assertEquals(32_000, result.getContextWindowSize());
        assertEquals(EncodingType.O200K_BASE, result.getEncodingType());
    }

    @Test
    void mergePicksRhsWhenLhsHasDefaultValues() {
        final var lhs = ModelAttributes.builder()
                .contextWindowSize(ModelAttributes.DEFAULT_WINDOW_SIZE)
                .encodingType(ModelAttributes.DEFAULT_ENCODING_TYPE)
                .build();
        final var rhs = ModelAttributes.builder()
                .contextWindowSize(64_000)
                .encodingType(EncodingType.O200K_BASE)
                .build();

        final var result = ModelAttributes.merge(lhs, rhs);

        // lhs has default values, so rhs values should be used
        assertEquals(64_000, result.getContextWindowSize());
        assertEquals(EncodingType.O200K_BASE, result.getEncodingType());
    }

    @Test
    void mergeRhsNullReturnsLhs() {
        final var lhs = ModelAttributes.builder()
                .contextWindowSize(32_000)
                .encodingType(EncodingType.O200K_BASE)
                .build();
        final var result = ModelAttributes.merge(lhs, null);
        assertSame(lhs, result);
    }
}
