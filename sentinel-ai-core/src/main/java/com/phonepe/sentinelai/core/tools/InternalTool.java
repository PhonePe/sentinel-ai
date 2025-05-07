package com.phonepe.sentinelai.core.tools;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class InternalTool extends ExecutableTool {

    ToolMethodInfo methodInfo;
    Object instance;

    public InternalTool(
            ToolDefinition toolDefinition,
            ToolMethodInfo toolMethodInfo,
            Object instance) {
        super(toolDefinition);
        this.instance = instance;
        this.methodInfo = toolMethodInfo;
    }

    @Override
    public <T> T accept(ExecutableToolVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
