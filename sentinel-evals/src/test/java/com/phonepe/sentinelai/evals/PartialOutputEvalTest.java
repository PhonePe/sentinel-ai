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

package com.phonepe.sentinelai.evals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.Expectations;
import com.phonepe.sentinelai.evals.tests.TestCase;

import lombok.NonNull;
import lombok.Value;
import lombok.val;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartialOutputEvalTest {
    @Value
    static class AgentDecision {
        String explanation;
        String status;
    }

    static class DecisionAgent extends Agent<String, AgentDecision, DecisionAgent> {
        protected DecisionAgent(@NonNull AgentSetup setup) {
            super(AgentDecision.class,
                  "Return an explanation and status",
                  setup,
                  List.<AgentExtension<String, AgentDecision, DecisionAgent>>of(),
                  Map.of());
        }

        @Override
        public String name() {
            return "decision-agent";
        }
    }

    static class DecisionModel implements Model {
        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            return CompletableFuture.supplyAsync(() -> {
                final var request = oldMessages.stream()
                        .filter(UserPrompt.class::isInstance)
                        .map(UserPrompt.class::cast)
                        .reduce((first, second) -> second)
                        .map(UserPrompt::getContent)
                        .orElse("");
                final var status = request.contains("fail") ? "FAILED" : "SUCCESS";
                final var explanation = "Detailed explanation for input: " + request;

                final var outputNode = JsonNodeFactory.instance.objectNode();
                outputNode.put("status", status);
                outputNode.put("explanation", explanation);

                final var data = JsonNodeFactory.instance.objectNode();
                data.set(Agent.OUTPUT_VARIABLE_NAME, outputNode);

                final var usage = new ModelUsageStats();
                final var allMessages = new ArrayList<>(oldMessages);
                return ModelOutput.success(data, List.of(), allMessages, usage);
            });
        }
    }

    @Test
    void testPartialOutputMatchingOnStatusOnly() {
        val agent = new DecisionAgent(AgentSetup.builder()
                .mapper(new ObjectMapper())
                .model(new DecisionModel())
                .build());

        val dataset = new Dataset("status-only-evals",
                                  List.of(
                                          new TestCase("all good",
                                                       List.of(Expectations.jsonPathEquals("$.status", "SUCCESS"))),
                                          new TestCase("please fail this",
                                                       List.of(Expectations.jsonPathEquals("$.status", "FAILED")))));

        val report = new EvalEngine().run(dataset, agent);

        assertEquals(2, report.getExecutedTestCases());
        assertEquals(2, report.getPassedTestCases());
        assertEquals(0, report.getFailedTestCases());
    }
}
