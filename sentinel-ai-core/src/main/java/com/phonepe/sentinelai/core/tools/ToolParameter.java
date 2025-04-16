package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.JavaType;
import lombok.NonNull;
import lombok.Value;

/**
 * Parameter to a tool that will be called by the LLM
 */
@Value
public class ToolParameter {
    @NonNull
    String name;
    @NonNull
    String description;
    @NonNull
    JavaType type;

}
