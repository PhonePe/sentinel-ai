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

package com.phonepe.sentinelai.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Tests {@link HuggingFaceTokenizerModel}
 */
class HuggingFaceTokenizerModelTest {
    @Test
    void testTokenize() {
        try (final var model = HuggingFaceTokenizerModel.builder().build()) {
            String[] tokens = model.tokenize("Hello, how are you?");
            assertNotNull(tokens);
            assertEquals(6, tokens.length, "Token count mismatch. Tokens: %s".formatted(String.join(", ", tokens)));
        }
    }

}
