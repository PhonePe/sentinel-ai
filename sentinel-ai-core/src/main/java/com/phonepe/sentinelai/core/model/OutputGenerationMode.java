package com.phonepe.sentinelai.core.model;

/**
 * Mode for generating output from an agent.
 */
public enum OutputGenerationMode {
    /**
     * Generate output using a tool. The output is passed as a parameter to the tool. This is the default mode for
     * output generation.
     */
    TOOL_BASED,

    /**
     * Generate output using a structured output format. The output is expected to be in a specific format. This is not
     * supported by many models and some models might not support tool call and structured output at the same time.
     */
    STRUCTURED_OUTPUT,
}
