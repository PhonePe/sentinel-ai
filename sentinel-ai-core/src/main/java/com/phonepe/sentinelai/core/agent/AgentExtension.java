package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.tools.ToolBox;
import lombok.Value;

import java.util.List;
import java.util.Optional;

/**
 *
 */
public interface AgentExtension extends ToolBox {

    @Value
    class ExtensionPromptSchema {
        List<SystemPromptSchema.SecondaryTask> tasks;
        List<Object> hints;
    }

    @Value
    class AgentExtensionOutputDefinition {
        String key;
        String description;
        JsonNode schema;
    }


    String name();
    <R, D, T, A extends Agent<R, D, T, A>> ExtensionPromptSchema additionalSystemPrompts(R request, AgentRequestMetadata metadata, A agent);
    Optional<AgentExtensionOutputDefinition> outputSchema();
    <R, D, T, A extends Agent<R, D, T, A>> void consume(final JsonNode output, A agent);
}
