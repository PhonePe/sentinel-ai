package com.phonepe.sentinelai.embedding;

/**
 *
 */
public interface TokenizerModel extends AutoCloseable {
    /**
     * Tokenize the input
     *
     * @param input The input to tokenize
     * @return The tokens
     */
    String[] tokenize(String input);
}
