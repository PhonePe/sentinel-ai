package com.phonepe.sentinelai.configuredagents;

/**
 * Modes of accessing agent metadata.
 */
public enum AgentMetadataAccessMode {
    /**
     * Tell the LLM to use the metadata included in the prompt in the facts section.
     */
    INCLUDE_IN_PROMPT,
    /**
     * Tell the LLM to use the metadata lookup tool to fetch agent metadata.
     */
    METADATA_TOOL_LOOKUP
}
