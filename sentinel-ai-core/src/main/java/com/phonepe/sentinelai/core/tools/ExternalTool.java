package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.errors.ErrorType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;

import java.util.function.BiFunction;

/**
 *
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ExternalTool extends ExecutableTool {
    public record ExternalToolResponse(Object response,
                                       ErrorType error) {
    }
    JsonNode parameterSchema;
    BiFunction<String, String, ExternalToolResponse> callable;

    public ExternalTool(ToolDefinition toolDefinition, JsonNode parameterSchema, BiFunction<String, String, ExternalToolResponse> callable) {
        super(toolDefinition);
        this.parameterSchema = parameterSchema;
        this.callable = callable;
    }

    @Override
    public <T> T accept(ExecutableToolVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
