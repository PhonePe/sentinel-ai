package com.phonepe.sentinelai.core.tools;

import lombok.*;

import java.lang.reflect.Method;
import java.util.Map;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InternalTool extends ExecutableTool {

    @Singular
    Map<String, ToolParameter> parameters;
    Method callable;
    Object instance;
    Class<?> returnType;

    @Builder
    public InternalTool(
            ToolDefinition toolDefinition,
            Map<String, ToolParameter> parameters,
            Method callable,
            Object instance,
            Class<?> returnType) {
        super(toolDefinition);
        this.parameters = parameters;
        this.callable = callable;
        this.instance = instance;
        this.returnType = returnType;
    }

    @Override
    public <T> T accept(ExecutableToolVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
