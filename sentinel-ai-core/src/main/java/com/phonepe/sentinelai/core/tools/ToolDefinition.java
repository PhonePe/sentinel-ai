package com.phonepe.sentinelai.core.tools;

import lombok.*;

import java.util.Map;

/**
 * Metadata for a tool that will be called by the LLM
 */
@Value
@With
@Builder
public class ToolDefinition {
    @NonNull
    String name;

    @NonNull
    String description;

    boolean contextAware;

    @Singular
    Map<String, ToolParameter> parameters;
}
