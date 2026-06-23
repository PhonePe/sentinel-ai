package com.phonepe.sentinelai.core.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentRunContext;
import com.phonepe.sentinelai.core.agent.FactList;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * @author ankush.nakaskar
 */
@Slf4j
public class ExternalToolAgentExtension <R, T, A extends Agent<R, T, A>> implements AgentExtension<R, T, A> {

    @Override
    public ExtensionPromptSchema additionalSystemPrompts(R request,
                                                         AgentRunContext<R> metadata,
                                                         A agent,
                                                         ProcessingMode processingMode) {
        return null;
    }

    @Override
    public void consume(JsonNode output, A agent) {
        log.info("Consuming the External tool agent");
    }

    @Override
    public List<FactList> facts(R request,
                                AgentRunContext<R> context,
                                A agent) {
        return List.of();
    }

    @Override
    public Optional<ModelOutputDefinition> outputSchema(ProcessingMode processingMode) {
        return Optional.empty();
    }

    @Override
    public String name() {
        return "external-tool-agent-extension";
    }
}
