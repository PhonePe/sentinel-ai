package configuredagents;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AgentExecutionResult {
    boolean successful;
    JsonNode error;
    JsonNode agentOutput;

    public static AgentExecutionResult success(JsonNode agentOutput) {
        return AgentExecutionResult.builder()
                .successful(true)
                .agentOutput(agentOutput)
                .build();
    }

    public static AgentExecutionResult fail(JsonNode error) {
        return AgentExecutionResult.builder()
                .successful(false)
                .error(error)
                .build();
    }
}
