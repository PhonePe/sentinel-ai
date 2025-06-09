package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

import java.util.List;

/**
 *
 */
@Value
@Builder
@Jacksonized
@JsonClassDescription("Output for memory extraction from conversations by the model")
public class AgentMemoryOutput {
    @JsonPropertyDescription("""
            List of memories extracted by the LLM from the conversation. These memories will be saved and retrieved by later by the agent to gain more intelligence over time.
            """)
    List<GeneratedMemoryUnit> generatedMemory;
}
