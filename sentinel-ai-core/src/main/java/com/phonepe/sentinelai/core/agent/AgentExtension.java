package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.phonepe.sentinelai.core.tools.ToolBox;
import lombok.Value;

import java.util.List;
import java.util.Optional;

/**
 * Can be used to extend the functionality of an agent. This can be used to add additional system prompts and facts.
 */
public interface AgentExtension extends ToolBox {

    @Value
    class ExtensionPromptSchema {
        @JacksonXmlElementWrapper(localName = "tasks")
        List<SystemPrompt.Task> task;
        List<Object> hints;
    }

    @Value
    class AgentExtensionOutputDefinition {
        String key;
        String description;
        JsonNode schema;
    }

    <R, T, A extends Agent<R, T, A>> List<FactList> facts(R request, AgentRequestMetadata metadata, A agent);
    <R, T, A extends Agent<R, T, A>> ExtensionPromptSchema additionalSystemPrompts(
            R request, AgentRequestMetadata metadata, A agent, ProcessingMode processingMode);
    Optional<AgentExtensionOutputDefinition> outputSchema(ProcessingMode processingMode);
    <R, T, A extends Agent<R, T, A>> void consume(final JsonNode output, A agent);
    <R, T, A extends Agent<R, T, A>> void onRegistrationCompleted(A agent);
}
