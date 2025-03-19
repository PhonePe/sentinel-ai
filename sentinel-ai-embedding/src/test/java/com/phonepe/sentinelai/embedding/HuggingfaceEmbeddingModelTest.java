package com.phonepe.sentinelai.embedding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 *
 */
class HuggingfaceEmbeddingModelTest {

    @Test
    void testEmbedding() {
        try(final var model = HuggingfaceEmbeddingModel.builder().build()) {
            float[] embedding = model.getEmbedding("Hello, how are you?");
            assertNotNull(embedding);
            assertEquals(384, embedding.length);
        }
    }

}