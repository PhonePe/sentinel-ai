/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.configuredagents;

import com.fasterxml.jackson.databind.JsonNode;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentInput;
import com.phonepe.sentinelai.core.agent.AgentOutput;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ApproveAllToolRuns;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errorhandling.DefaultErrorHandler;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.outputvalidation.DefaultOutputValidator;
import com.phonepe.sentinelai.core.tools.ToolBox;

import lombok.SneakyThrows;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A configured agent is an envelope used by the Agent Registry to manage dynamically configured agents.
 */
@SuppressWarnings({
        "unused", "FieldCanBeLocal"
})
public class ConfiguredAgent {

    public static final class RootAgent extends RegisterableAgent<RootAgent> {

        private final AgentConfiguration agentConfiguration;

        public RootAgent(final AgentConfiguration agentConfiguration,
                         final AgentSetup agentSetup,
                         final List<AgentExtension<String, String, RootAgent>> agentExtensions,
                         final ToolBox toolBox) {
            super(agentConfiguration,
                  agentSetup,
                  agentExtensions,
                  Map.of(),
                  new ApproveAllToolRuns<>(),
                  new DefaultOutputValidator<>(),
                  new DefaultErrorHandler<>(),
                  new NeverTerminateEarlyStrategy());
            this.agentConfiguration = agentConfiguration;
            this.registerToolbox(toolBox);
        }

    }

    private final Agent<String, String, ? extends RegisterableAgent<?>> rootAgent;

    public ConfiguredAgent(Agent<String, String, ? extends RegisterableAgent<?>> agent) {
        this.rootAgent = agent;
    }

    public ConfiguredAgent(final AgentConfiguration agentConfiguration,
                           final List<AgentExtension<String, String, RootAgent>> rootAgentExtensions,
                           final ToolBox availableTools,
                           final AgentSetup agentSetup) {
        this.rootAgent = new RootAgent(agentConfiguration,
                                       agentSetup,
                                       rootAgentExtensions,
                                       availableTools);
    }

    @SneakyThrows
    public final CompletableFuture<AgentOutput<JsonNode>> executeAsync(AgentInput<JsonNode> input) {
        final var mapper = input.getAgentSetup().getMapper();
        return rootAgent.executeAsync(new AgentInput<>(mapper
                .writeValueAsString(input.getRequest()),
                                                       input.getFacts(),
                                                       input.getRequestMetadata(),
                                                       input.getOldMessages(),
                                                       null // We do not forward the setup here to use setup from rootAgent
        )).thenApply(output -> {
            try {
                final var error = output.getError();
                if (error != null && !error.getErrorType()
                        .equals(ErrorType.SUCCESS)) {
                    return new AgentOutput<>(null,
                                             output.getNewMessages(),
                                             output.getAllMessages(),
                                             output.getUsage(),
                                             error);
                }
                final var json = mapper.readTree(output.getData());
                return new AgentOutput<>(json,
                                         output.getNewMessages(),
                                         output.getAllMessages(),
                                         output.getUsage(),
                                         output.getError());
            }
            catch (Exception e) {
                throw new RuntimeException("Failed to parse AgentOutput response to JsonNode",
                                           e);
            }
        });
    }

    public ConfiguredAgent registerAgentMessagesPreProcessors(List<AgentMessagesPreProcessor> preProcessors) {
        rootAgent.registerAgentMessagesPreProcessors(Objects
                .requireNonNullElseGet(preProcessors, List::of));
        return this;
    }
}
