package com.phonepe.sentinelai.core.tools;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * A record to hold information about a tool method.
 */
public record ToolMethodInfo(
        Map<String, ToolParameter> parameters,
        Method callable,
        Class<?> returnType) {}
