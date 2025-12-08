package com.phonepe.sentinelai.configuredagents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ApproveAllToolRuns;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errorhandling.DefaultErrorHandler;
import com.phonepe.sentinelai.core.errorhandling.ErrorResponseHandler;
import com.phonepe.sentinelai.core.outputvalidation.DefaultOutputValidator;
import com.phonepe.sentinelai.core.outputvalidation.OutputValidator;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.ToolRunApprovalSeeker;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An agent that can be registered in the {@link AgentRegistry}.
 * @param <T> The type of the agent extending this class.
 */
@SuppressWarnings("java:S107")
public abstract class RegisterableAgent<T  extends RegisterableAgent<T>> extends Agent<String, String, T> {

    private final AgentConfiguration agentConfiguration;

    protected RegisterableAgent(
            AgentConfiguration agentConfiguration,
            @NonNull AgentSetup setup,
            List<AgentExtension<String, String, T>> agentExtensions,
            Map<String, ExecutableTool> knownTools) {
        this(agentConfiguration,
             setup,
             agentExtensions,
             knownTools,
             new ApproveAllToolRuns<>(),
             new DefaultOutputValidator<>(),
             new DefaultErrorHandler<>(),
             new NeverTerminateEarlyStrategy());
    }

    protected RegisterableAgent(
            AgentConfiguration agentConfiguration,
            @NonNull AgentSetup setup,
            List<AgentExtension<String, String, T>> agentExtensions,
            Map<String, ExecutableTool> knownTools,
            ToolRunApprovalSeeker<String, String, T> toolRunApprovalSeeker,
            OutputValidator<String, String> outputValidator,
            ErrorResponseHandler<String> errorHandler,
            EarlyTerminationStrategy earlyTerminationStrategy) {
        super(String.class,
              agentConfiguration.getPrompt(),
              setup,
              agentExtensions,
              knownTools,
              toolRunApprovalSeeker,
              outputValidator,
              errorHandler,
              earlyTerminationStrategy);
        this.agentConfiguration = AgentConfiguration.fixConfiguration(
                agentConfiguration, Objects.requireNonNullElseGet(setup.getMapper(), JsonUtils::createMapper));
    }

    public final AgentConfiguration agentConfiguration() {
        return agentConfiguration;
    }


    /**
     * You cannot override this here. Set the correct schema in the agent configuration.
     * @return The output schema for this agent
     */
    @Override
    protected final JsonNode outputSchema() {
        return agentConfiguration.getOutputSchema();
    }

    /**
     * The name of the agent as specified in the configuration. Feel free to override if needed for more exotic
     * implementations
     * @return The name of the agent
     */
    @Override
    public String name() {
        return agentConfiguration.getAgentName();
    }

    /**
     * We don't do much here. Basically if the model sends out put in the required format, we just pass it through.
     * You can override this if something more exotic needs to be done
     * @param output     The model output
     * @param agentSetup The agent setup
     * @return A String serialized version of the output. It is a little wasteful to serialize and deserialize again, but
     *         this keeps things simple.
     * @throws JsonProcessingException If serialization fails
     */
    @Override
    protected String translateData(JsonNode output, AgentSetup agentSetup) throws JsonProcessingException {
        return agentSetup.getMapper()
                .writeValueAsString(output);
    }
}
