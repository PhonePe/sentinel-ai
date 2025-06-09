package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Representation of a memory for an agent
 */
@Value
@JsonClassDescription("memory to be used by AI Agents to learn new constructs and personalize conversations with users")
@Builder
public class AgentMemory {
    @JsonPropertyDescription("Agent for whom this memory is relevant")
    String agentName;

    @JsonPropertyDescription("Scope of the memory. Whether it is entity specific or specific to the agent itself")
    MemoryScope scope;

    @JsonPropertyDescription("For entity scoped memories, this will signify what entity it is scoped to. for agent memory, this will be set to agent name")
    String scopeId;

    @JsonPropertyDescription("Whether the memory is semantic, episodic or procedural")
    MemoryType memoryType;

    @JsonPropertyDescription("Name of the memory. Will be used as key for updating the memory in the scope")
    String name;

    @JsonPropertyDescription("The actual content of the memory")
    String content;

    @JsonPropertyDescription("Topics associated with the memory")
    List<String> topics;

    @JsonPropertyDescription("A score that indicates how reusable the information is. Score 0 means the information is " +
            "not reusable and score 10 means the information is highly reusable")
    int reusabilityScore;

    @JsonPropertyDescription("When the memory was created")
    LocalDateTime createdAt;

    @JsonPropertyDescription("When the memory was last updated")
    LocalDateTime updatedAt;
}
