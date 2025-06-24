package com.phonepe.sentinelai.agentmemory;

/**
 * Enum representing the mode of memory extraction.
 */
public enum MemoryExtractionMode {
    /**
     * Memory is extracted inline, meaning it is directly included in the response.
     */
    INLINE,
    /**
     * Memory is extracted out of band, meaning memory is extracted using a separate call to the LLM out of band.
     * In case of streaming execution of models, extraction is always out of band.
     */
    OUT_OF_BAND,
    /**
     * Memory is not extracted at all. This is useful in agents which work off memories gathered in some kind of
     * training mode and used in inference mode.
     */
    DISABLED
}
