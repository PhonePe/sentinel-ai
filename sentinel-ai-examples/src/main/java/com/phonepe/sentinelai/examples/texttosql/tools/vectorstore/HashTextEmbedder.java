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

/**
 * Lightweight text embedder based on feature hashing.
 *
 * <p>Converts a text string into a fixed-dimension float vector using a combination of word-level
 * and character 3-gram features hashed into {@link #VECTOR_DIM} buckets, then L2-normalised. The
 * embedding is deterministic and requires no external models or network calls.
 *
 * <p>While not as expressive as transformer-based embeddings, this approach captures:
 *
 * <ul>
 *   <li>Exact word matches (via word-level hashing)
 *   <li>Morphological similarity — e.g. "order" and "ordered" share many 3-gram buckets
 *   <li>Sub-word patterns shared across domain-specific terms
 * </ul>
 */
public class HashTextEmbedder {

    /** Dimensionality of the produced embedding vectors. */
    public static final int VECTOR_DIM = 128;

    private static final float WORD_WEIGHT = 1.0f;
    private static final float TRIGRAM_WEIGHT = 0.5f;

    /**
     * Embeds {@code text} into a {@link #VECTOR_DIM}-dimensional L2-normalised float vector.
     *
     * @param text the input text; {@code null} or blank returns a zero vector
     * @return a float array of length {@link #VECTOR_DIM}
     */
    public float[] embed(String text) {
        float[] vector = new float[VECTOR_DIM];
        if (text == null || text.isBlank()) {
            return vector;
        }

        // Normalise: lowercase, replace non-alphanumeric with spaces
        String normalized = text.toLowerCase().replaceAll("[^a-z0-9]", " ");
        String[] tokens = normalized.trim().split("\\s+");

        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }

            // Word-level feature: map the whole token to one dimension
            int wordIdx = Math.abs(token.hashCode()) % VECTOR_DIM;
            vector[wordIdx] += WORD_WEIGHT;

            // Character 3-gram features: capture sub-word and morphological patterns
            for (int i = 0; i <= token.length() - 3; i++) {
                String trigram = token.substring(i, i + 3);
                int trigramIdx = Math.abs(trigram.hashCode()) % VECTOR_DIM;
                vector[trigramIdx] += TRIGRAM_WEIGHT;
            }
        }

        l2Normalize(vector);
        return vector;
    }

    private static void l2Normalize(float[] vector) {
        float sumSq = 0.0f;
        for (float v : vector) {
            sumSq += v * v;
        }
        if (sumSq > 0.0f) {
            float norm = (float) Math.sqrt(sumSq);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= norm;
            }
        }
    }
}
