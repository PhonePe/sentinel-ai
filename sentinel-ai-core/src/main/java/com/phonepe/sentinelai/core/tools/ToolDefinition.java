package com.phonepe.sentinelai.core.tools;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

/**
 * Metadata for a tool that will be called by the LLM
 */
@Value
@With
@Builder
public class ToolDefinition {
    @NonNull
    String id;

    @NonNull
    String name;

    @NonNull
    String description;

    boolean contextAware;

    boolean strictSchema;
}
