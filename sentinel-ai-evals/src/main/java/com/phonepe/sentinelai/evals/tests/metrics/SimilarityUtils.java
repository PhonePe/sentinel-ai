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

package com.phonepe.sentinelai.evals.tests.metrics;

import lombok.experimental.UtilityClass;

@UtilityClass
final class SimilarityUtils {


    static double cosineSimilarity(float[] lhs, float[] rhs) {
        final var dotProduct = getDotProduct(lhs, rhs);

        var magnitude1 = 0.0;
        var magnitude2 = 0.0;
        for (var v : lhs) {
            magnitude1 += v * v;
        }
        for (var v : rhs) {
            magnitude2 += v * v;
        }

        magnitude1 = Math.sqrt(magnitude1);
        magnitude2 = Math.sqrt(magnitude2);

        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0;
        }

        final var similarity = dotProduct / (magnitude1 * magnitude2);
        return Math.max(0.0, Math.min(1.0, similarity));
    }

    private static double getDotProduct(float[] lhs, float[] rhs) {
        if (lhs == null || rhs == null) {
            throw new IllegalArgumentException(
                                               "Input vectors cannot be null [lhs is null: %b, rhs is null: %b]"
                                                       .formatted(lhs == null,
                                                                  rhs == null));
        }

        final var minLength = Math.min(lhs.length, rhs.length);
        if (minLength == 0 || lhs.length != rhs.length) {
            throw new IllegalArgumentException(
                                               "Vectors must be non-empty and of the same length [lhs length %d, rhs length %d]"
                                                       .formatted(lhs.length,
                                                                  rhs.length));
        }

        var dotProduct = 0.0;
        for (var i = 0; i < minLength; i++) {
            dotProduct += lhs[i] * rhs[i];
        }
        return dotProduct;
    }
}
