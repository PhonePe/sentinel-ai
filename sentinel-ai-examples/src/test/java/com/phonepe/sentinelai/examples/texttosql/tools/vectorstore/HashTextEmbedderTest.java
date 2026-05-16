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

package com.phonepe.sentinelai.examples.texttosql.tools.vectorstore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class HashTextEmbedderTest {

    @Nested
    class Determinism {

        @Test
        void differentInputsProduceDifferentVectors() {
            float[] a = embedder.embed("users email address");
            float[] b = embedder.embed("orders delivery shipped");
            assertFalse(
                        java.util.Arrays.equals(a, b),
                        "different inputs should produce different vectors");
        }

        @Test
        void sameInputProducesSameVector() {
            float[] first = embedder.embed("inventory warehouse stock quantity");
            float[] second = embedder.embed("inventory warehouse stock quantity");
            assertArrayEquals(first, second, "embedding must be deterministic");
        }
    }

    @Nested
    class EdgeCases {

        @Test
        void numericTextHandled() {
            float[] result = embedder.embed("12345 67890");
            assertNotNull(result);
            assertEquals(HashTextEmbedder.VECTOR_DIM, result.length);
        }

        @Test
        void shortTokenProducesValidVector() {
            float[] result = embedder.embed("ab");
            assertNotNull(result);
            assertEquals(HashTextEmbedder.VECTOR_DIM, result.length);
            // "ab" has no 3-grams; only word hash bucket gets weight
            float sumSq = 0.0f;
            for (float v : result) {
                sumSq += v * v;
            }
            assertEquals(1.0f, sumSq, 1e-5f, "single-word short token should be normalised");
        }

        @Test
        void specialCharactersOnlyReturnsZeroVector() {
            // All non-alphanumeric chars get replaced with spaces; only whitespace remains
            float[] result = embedder.embed("!@#$%^&*()");
            float sumSq = 0.0f;
            for (float v : result) {
                sumSq += v * v;
            }
            // After stripping non-alphanum, the string becomes spaces → blank → zero vector
            assertEquals(0.0f, sumSq, 1e-6f);
        }

        @Test
        void uppercaseIsCaseInsensitive() {
            float[] lower = embedder.embed("order shipped delivered");
            float[] upper = embedder.embed("ORDER SHIPPED DELIVERED");
            assertArrayEquals(lower, upper, "embedding should be case-insensitive");
        }

        @Test
        void veryLongTextHandledGracefully() {
            String longText = "word ".repeat(1000);
            assertDoesNotThrow(() -> embedder.embed(longText));
        }
    }

    @Nested
    class NullAndBlankInputs {

        @Test
        void blankInputReturnsZeroVector() {
            float[] result = embedder.embed("   ");
            assertNotNull(result);
            assertEquals(HashTextEmbedder.VECTOR_DIM, result.length);
            for (float v : result) {
                assertEquals(0.0f, v, "blank input should produce all-zero vector");
            }
        }

        @Test
        void emptyStringReturnsZeroVector() {
            float[] result = embedder.embed("");
            assertNotNull(result);
            assertEquals(HashTextEmbedder.VECTOR_DIM, result.length);
            for (float v : result) {
                assertEquals(0.0f, v);
            }
        }

        @Test
        void nullInputReturnsZeroVector() {
            float[] result = embedder.embed(null);
            assertNotNull(result);
            assertEquals(HashTextEmbedder.VECTOR_DIM, result.length);
            for (float v : result) {
                assertEquals(0.0f, v, "null input should produce all-zero vector");
            }
        }
    }

    @Nested
    class OutputShape {

        @Test
        void outputHasCorrectDimension() {
            float[] result = embedder.embed("hello world");
            assertEquals(HashTextEmbedder.VECTOR_DIM, result.length);
        }

        @Test
        void outputIsL2Normalised() {
            float[] result = embedder.embed("the quick brown fox jumps over the lazy dog");
            float sumSq = 0.0f;
            for (float v : result) {
                sumSq += v * v;
            }
            assertEquals(1.0f, sumSq, 1e-5f, "L2 norm should be ~1.0 for non-empty text");
        }

        @Test
        void singleWordIsNormalised() {
            float[] result = embedder.embed("order");
            float sumSq = 0.0f;
            for (float v : result) {
                sumSq += v * v;
            }
            assertEquals(1.0f, sumSq, 1e-5f);
        }

        @Test
        void vectorDimIs128() {
            assertEquals(128, HashTextEmbedder.VECTOR_DIM);
        }
    }

    private HashTextEmbedder embedder;

    @BeforeEach
    void setUp() {
        embedder = new HashTextEmbedder();
    }
}
