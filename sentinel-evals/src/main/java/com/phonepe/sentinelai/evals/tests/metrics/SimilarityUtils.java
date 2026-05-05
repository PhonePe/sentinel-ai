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

final class SimilarityUtils {

    private SimilarityUtils() {
    }

    static double cosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null) {
            return 0.0;
        }

        final int minLength = Math.min(vector1.length, vector2.length);
        if (minLength == 0) {
            return 0.0;
        }

        double dotProduct = 0.0;
        for (int i = 0; i < minLength; i++) {
            dotProduct += vector1[i] * vector2[i];
        }

        double magnitude1 = 0.0;
        double magnitude2 = 0.0;
        for (float v : vector1) {
            magnitude1 += v * v;
        }
        for (float v : vector2) {
            magnitude2 += v * v;
        }

        magnitude1 = Math.sqrt(magnitude1);
        magnitude2 = Math.sqrt(magnitude2);

        if (magnitude1 == 0.0 || magnitude2 == 0.0) {
            return 0.0;
        }

        final double similarity = dotProduct / (magnitude1 * magnitude2);
        return Math.max(0.0, Math.min(1.0, similarity));
    }
}
