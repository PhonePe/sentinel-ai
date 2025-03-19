package com.phonepe.sentinelai.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests {@link HuggingFaceTokenizerModel}
 */
class HuggingFaceTokenizerModelTest {
    @Test
    void testTokenize() {
        try(final var model = HuggingFaceTokenizerModel.builder().build()) {
            String[] tokens = model.tokenize("Hello, how are you?");
            assertNotNull(tokens);
            assertEquals(6, tokens.length, "Token count mismatch. Tokens: %s".formatted(String.join(", ", tokens)));
        }
    }

}