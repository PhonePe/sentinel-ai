package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.tools.ToolBox;

import java.util.List;

/**
 *
 */
public interface AgentExtension extends ToolBox {
    <R> List<String> systemPrompts(R request, AgentRequestMetadata metadata);
    JsonNode outputSchema();
    void consume(final JsonNode output);
    String outputKey();
}
