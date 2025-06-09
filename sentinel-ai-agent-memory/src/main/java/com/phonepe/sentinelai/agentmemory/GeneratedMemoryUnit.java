package com.phonepe.sentinelai.agentmemory;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Value;

import java.util.List;

/**
 * A quantum of memory extracted from conversations by the model
 */
@Value
@JsonClassDescription("A quantum of memory extracted from conversations by the model")
public class GeneratedMemoryUnit {
    @JsonPropertyDescription("Scope of the memory")
    MemoryScope scope;

    @JsonPropertyDescription
    String scopeId;

    @JsonPropertyDescription("Type of memory: Semantic/Procedural/Episodic etc")
    MemoryType type;

    @JsonPropertyDescription("CamelCase keyword indicating name of the memory. Will be used as key for updating the memory in the scope")
    String name;
    @JsonPropertyDescription("""
            The actual content of the memory. Structured in prompt format so that it can be used in system prompt
             in later sessions
            """)
    String content;
    @JsonPropertyDescription("Topics associated with the memory")
    List<String> topics;

    @JsonPropertyDescription("""
            A score that indicates how reusable the information is. Score 0 means the information is not reusable and
             score 10 means the information is highly reusable
            """)
    int reusabilityScore;
}
