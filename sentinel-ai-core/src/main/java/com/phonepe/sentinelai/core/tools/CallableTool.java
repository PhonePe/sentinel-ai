package com.phonepe.sentinelai.core.tools;

import lombok.Value;

import java.lang.reflect.Method;

/**
 *
 */
@Value
public class CallableTool {
    ToolDefinition toolDefinition;
    Method callable;
    Object instance;
    Class<?> returnType;
}
