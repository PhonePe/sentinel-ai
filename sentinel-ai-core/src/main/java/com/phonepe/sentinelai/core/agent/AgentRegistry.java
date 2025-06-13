package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.core.tools.ToolBox;

/**
 *
 */
public abstract class AgentRegistry<A extends AgentRegistry<A>> implements ToolBox {
    abstract A registerAgent(Agent<?, ?, ?> agent);

    @Tool("Get an agent based on query")
    public  AgentMetadata findAgent(@JsonPropertyDescription("A query to find agent based on requirements") String query) {
        return null;
    }

    @Tool("Invoke an agent with input")
    public JsonNode invokeAgent(
            String agentName,
            JsonNode input) {
        return null;
    }
}
