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
public interface AgentExtension<R, T, A extends Agent<R, T, A>> extends ToolBox {

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

    List<FactList> facts(R request, AgentRequestMetadata metadata, A agent);

    ExtensionPromptSchema additionalSystemPrompts(
            R request, AgentRequestMetadata metadata, A agent, ProcessingMode processingMode);

    Optional<AgentExtensionOutputDefinition> outputSchema(ProcessingMode processingMode);

    void consume(final JsonNode output, A agent);

    default void onExtensionRegistrationCompleted(A agent) {
        //Nothing to do here for now
    }

}
