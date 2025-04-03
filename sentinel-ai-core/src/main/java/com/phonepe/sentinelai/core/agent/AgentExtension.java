package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
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
        @JacksonXmlElementWrapper(localName = "tasks")
        List<SystemPrompt.SecondaryTask> task;
        List<Object> hints;
    }

    @Value
    class AgentExtensionOutputDefinition {
        String key;
        String description;
        JsonNode schema;
    }


    String name();
    <R, T, A extends Agent<R, T, A>> List<SystemPrompt.FactList> facts(R request, AgentRequestMetadata metadata, A agent);
    <R, T, A extends Agent<R, T, A>> ExtensionPromptSchema additionalSystemPrompts(R request, AgentRequestMetadata metadata, A agent);
    Optional<AgentExtensionOutputDefinition> outputSchema();
    <R, T, A extends Agent<R, T, A>> void consume(final JsonNode output, A agent);
}
