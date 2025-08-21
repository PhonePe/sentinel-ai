package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.requests.ToolCallResponse;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.errors.ErrorType;
import lombok.SneakyThrows;
import org.apache.commons.lang3.NotImplementedException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * This is a trivial implementation of a tool runner that can be used in sampling, async etc paths to use function
 * calling for output generation.
 */
public class NonContextualDefaultExternalToolRunner implements ToolRunner {
    private final ObjectMapper mapper;

    public NonContextualDefaultExternalToolRunner(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ToolCallResponse runTool(Map<String, ExecutableTool> tools, ToolCall toolCall) {
        final var tool = Objects.requireNonNull(tools.get(toolCall.getToolName()));
        return tool.accept(new ExecutableToolVisitor<ToolCallResponse>() {
            @Override
            @SneakyThrows
            public ToolCallResponse visit(ExternalTool externalTool) {
                final var response =
                        externalTool.getCallable()
                                .apply(null,
                                       toolCall.getToolCallId(),
                                       toolCall.getArguments());
                return new ToolCallResponse(toolCall.getToolCallId(),
                                            toolCall.getToolName(),
                                            ErrorType.SUCCESS,
                                            mapper.writeValueAsString(response.response()),
                                            LocalDateTime.now());

            }

            @Override
            public ToolCallResponse visit(InternalTool internalTool) {
                throw new NotImplementedException();
            }
        });
    }
}
