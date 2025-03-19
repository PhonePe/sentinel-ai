package com.phonepe.sentinelai.embedding;

/**
 * A representation for an embedding model
 */
public interface EmbeddingModel extends AutoCloseable {
    /**
     * Get the embedding for the given input
     *
     * @param input The input to get the embedding for
     * @return The embedding for the input
     */
    float[] getEmbedding(String input);
}
