package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
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

    List<FactList> facts(R request, AgentRunContext<R> metadata, A agent);

    ExtensionPromptSchema additionalSystemPrompts(
            R request, AgentRunContext<R> metadata, A agent, ProcessingMode processingMode);

    Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode);

    void consume(final JsonNode output, A agent);

    default List<AgentMessage> messages(R request, AgentRunContext<R> metadata, A agent) {
        return List.of();
    }

    default void onExtensionRegistrationCompleted(A agent) {
        //Nothing to do here for now
    }

}
