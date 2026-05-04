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
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.agentmessages.responses.ToolCall;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.Expectations;
import com.phonepe.sentinelai.evals.tests.TestCase;
import com.phonepe.sentinelai.evals.tests.expectations.ToolCalledExpectation;

import lombok.NonNull;
import lombok.val;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class EvalEngineTest {
    static class OrderedToolCallModel implements Model {
        private final List<String> encodedToolOrder;
        private final String finalOutput;

        OrderedToolCallModel(List<String> encodedToolOrder,
                             String finalOutput) {
            this.encodedToolOrder = encodedToolOrder;
            this.finalOutput = finalOutput;
        }

        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            return CompletableFuture.supplyAsync(() -> {
                val usage = new ModelUsageStats();
                ObjectNode data = JsonNodeFactory.instance.objectNode();
                data.put(Agent.OUTPUT_VARIABLE_NAME, finalOutput);

                final var sessionId = Objects.requireNonNullElse(context.getSessionId(), "session");
                final var runId = Objects.requireNonNullElse(context.getRunId(), "run");
                final var newMessages = new ArrayList<AgentMessage>();
                for (int i = 0; i < encodedToolOrder.size(); i++) {
                    newMessages.add(new ToolCall(sessionId,
                                                 runId,
                                                 "tc-" + i,
                                                 encodedToolOrder.get(i),
                                                 "{}"));
                }
                newMessages.add(new Text(sessionId,
                                         runId,
                                         finalOutput,
                                         usage,
                                         0));

                final var allMessages = new ArrayList<>(oldMessages);
                allMessages.addAll(newMessages);
                return ModelOutput.success(data,
                                           newMessages,
                                           allMessages,
                                           usage);
            });
        }
    }

    static class TestAgent extends Agent<String, String, TestAgent> {
        protected TestAgent(@NonNull Class<String> outputType,
                            @NonNull String systemPrompt,
                            @NonNull AgentSetup setup,
                            List<AgentExtension<String, String, TestAgent>> agentExtensions,
                            Map<String, ExecutableTool> knownTools) {
            super(outputType, systemPrompt, setup, agentExtensions, knownTools);
        }

        @Tool(name = "fetch_account", value = "Fetches account details")
        public String fetchAccount() {
            return "account";
        }

        @Tool(name = "fetch_user", value = "Fetches user details")
        public String fetchUser() {
            return "user";
        }

        @Override
        public String name() {
            return "test-agent";
        }
    }

    static class TestModel implements Model {
        private final String output;
        private final long delayMs;

        TestModel(String output,
                  long delayMs) {
            this.output = output;
            this.delayMs = delayMs;
        }

        private static void sleep(long delayMs) {
            if (delayMs <= 0) {
                return;
            }
            try {
                Thread.sleep(delayMs);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            return CompletableFuture.supplyAsync(() -> {
                sleep(delayMs);
                val usage = new ModelUsageStats();
                ObjectNode data = JsonNodeFactory.instance.objectNode();
                data.put(Agent.OUTPUT_VARIABLE_NAME, output);
                val newMessages = List.<AgentMessage>of(new Text(context.getSessionId(),
                                                                 context.getRunId(),
                                                                 output,
                                                                 usage,
                                                                 delayMs));
                val allMessages = new ArrayList<>(oldMessages);
                allMessages.addAll(newMessages);
                return ModelOutput.success(data,
                                           newMessages,
                                           allMessages,
                                           usage);
            });
        }
    }

    private static TestAgent buildAgent(Model model) {
        val mapper = new ObjectMapper();
        return new TestAgent(String.class,
                             "System prompt",
                             AgentSetup.builder()
                                     .mapper(mapper)
                                     .model(model)
                                     .build(),
                             List.of(),
                             Map.of());
    }

    private static TestAgent buildAgent(String output,
                                        long delayMs) {
        val mapper = new ObjectMapper();
        return new TestAgent(String.class,
                             "System prompt",
                             AgentSetup.builder()
                                     .mapper(mapper)
                                     .model(new TestModel(output, delayMs))
                                     .build(),
                             List.of(),
                             Map.of());
    }

    @Test
    void testFailFast() {
        EvalEngine engine = new EvalEngine();
        val tests = List.of(
                            new TestCase("first", List.of(Expectations.outputContains("missing"))),
                            new TestCase("second", List.of(Expectations.outputContains("ok"))));
        val dataset = new Dataset("test-dataset", tests);

        val report = engine.run(dataset,
                                buildAgent("ok", 0),
                                EvalRunConfig.builder()
                                        .failFast(true)
                                        .build());

        assertEquals(2, report.getSampledTestCases());
        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getFailedTestCases());
        assertFalse(report.isCompletedAllSampledCases());
    }

    @Test
    void testSampling() {
        EvalEngine engine = new EvalEngine();
        val tests = new ArrayList<TestCase>();
        for (int i = 0; i < 10; i++) {
            tests.add(new TestCase("input-" + i,
                                   List.of(Expectations.outputContains("ok"))));
        }
        val dataset = new Dataset("test-dataset", tests);
        val report = engine.run(dataset,
                                buildAgent("ok", 0),
                                EvalRunConfig.builder()
                                        .samplePercentage(30)
                                        .sampleSeed(7L)
                                        .build());

        assertEquals(10, report.getTotalTestCases());
        assertEquals(3, report.getSampledTestCases());
        assertEquals(3, report.getExecutedTestCases());
        assertEquals(3, report.getPassedTestCases());
        assertTrue(report.isCompletedAllSampledCases());
    }

    @Test
    void testTimeoutMarksSkipped() {
        EvalEngine engine = new EvalEngine();
        val tests = List.of(new TestCase("slow",
                                         List.of(Expectations.outputContains("ok")),
                                         Duration.ofMillis(50)));
        val dataset = new Dataset("test-dataset", tests);

        val report = engine.run(dataset,
                                buildAgent("ok", 200),
                                EvalRunConfig.defaults());

        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getSkippedTestCases());
        assertEquals(EvalStatus.SKIPPED, report.getTestCaseReports().get(0).getStatus());
        assertTrue(report.getTestCaseReports().get(0).getDetails().contains("Timed out"));
    }

    @Test
    void testToolCallsFollowEncodedOrder() {
        EvalEngine engine = new EvalEngine();
        val orderedExpectation = Expectations.ordered(
                                                      new ToolCalledExpectation<>("fetch_user"),
                                                      new ToolCalledExpectation<>("fetch_account"));
        val tests = List.of(new TestCase("run tools", List.of(orderedExpectation)));
        val dataset = new Dataset("tool-order-dataset", tests);

        val report = engine.run(dataset,
                                buildAgent(new OrderedToolCallModel(List.of("fetch_user", "fetch_account"),
                                                                    "ok")),
                                EvalRunConfig.defaults());

        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getPassedTestCases());
        assertEquals(0, report.getFailedTestCases());
    }
}
