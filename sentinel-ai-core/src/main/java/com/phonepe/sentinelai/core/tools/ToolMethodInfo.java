package com.phonepe.sentinelai.core.tools;

import java.lang.reflect.Method;
import java.util.List;

/**
 * A record to hold information about a tool method.
 */
public record ToolMethodInfo(
        List<ToolParameter> parameters,
        Method callable,
        Class<?> returnType) {}
