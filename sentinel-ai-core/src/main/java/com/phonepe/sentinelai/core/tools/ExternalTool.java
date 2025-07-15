package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.errors.ErrorType;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.Value;
import org.apache.commons.lang3.function.TriFunction;

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
    TriFunction<AgentRunContext<?>, String, String, ExternalToolResponse> callable;

    public ExternalTool(ToolDefinition toolDefinition, JsonNode parameterSchema, TriFunction<AgentRunContext<?>, String, String, ExternalToolResponse> callable) {
        super(toolDefinition);
        this.parameterSchema = parameterSchema;
        this.callable = callable;
    }

    @Override
    public <T> T accept(ExecutableToolVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
