package com.phonepe.sentinelai.core.model;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.phonepe.sentinelai.core.agentmemory.MemoryOutput;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 *
 */
@Data
@AllArgsConstructor
@JsonClassDescription("The output format for model")
public class ModelOutput<T, M extends ModelOutput<T,M>> {
    @JsonPropertyDescription("Response for the main computation")
    private T output;

    @JsonPropertyDescription("Memories extracted from conversation by the model")
    private MemoryOutput memoryOutput;


}
