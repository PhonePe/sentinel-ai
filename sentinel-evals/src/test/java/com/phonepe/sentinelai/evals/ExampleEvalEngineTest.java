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
import com.phonepe.sentinelai.embedding.EmbeddingModel;
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.Expectation;
import com.phonepe.sentinelai.evals.tests.Expectations;
import com.phonepe.sentinelai.evals.tests.TestCase;

import lombok.NonNull;
import lombok.Value;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleEvalEngineTest {

    @Value
    static class DecisionOutput {
        String status;
        int score;
        int attempts;
        String category;
    }

    static class ExampleAnswerRelevanceJudgeModel implements Model {
        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            return CompletableFuture.supplyAsync(() -> {
                final var usage = new ModelUsageStats();
                final var data = JsonNodeFactory.instance.objectNode();
                data.put(Agent.OUTPUT_VARIABLE_NAME, "{\"score\":0.96,\"reason\":\"Answer covers request intent\"}");
                return ModelOutput.success(data,
                                           List.of(),
                                           oldMessages,
                                           usage);
            });
        }
    }

    static class ExampleDecisionAgent extends Agent<String, DecisionOutput, ExampleDecisionAgent> {
        protected ExampleDecisionAgent(@NonNull AgentSetup setup) {
            super(DecisionOutput.class,
                  "Return status, score, attempts and category",
                  setup,
                  List.of(),
                  Map.of());
        }

        @Override
        public String name() {
            return "example-decision-agent";
        }
    }

    static class ExampleDecisionModel implements Model {
        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            return CompletableFuture.supplyAsync(() -> {
                final var usage = new ModelUsageStats();

                final var outputNode = JsonNodeFactory.instance.objectNode();
                outputNode.put("status", "SUCCESS");
                outputNode.put("score", 92);
                outputNode.put("attempts", 3);
                outputNode.put("category", "GOLD");

                final var data = JsonNodeFactory.instance.objectNode();
                data.set(Agent.OUTPUT_VARIABLE_NAME, outputNode);

                final var text = new Text(context.getSessionId(),
                                          context.getRunId(),
                                          "Decision generated",
                                          usage,
                                          0);

                final var allMessages = new ArrayList<>(oldMessages);
                allMessages.add(text);

                return ModelOutput.success(data,
                                           List.of(text),
                                           allMessages,
                                           usage);
            });
        }
    }

    static class ExampleStringAgent extends Agent<String, String, ExampleStringAgent> {
        protected ExampleStringAgent(@NonNull AgentSetup setup) {
            super(String.class,
                  "Return a summary string",
                  setup,
                  List.of(),
                  Map.of());
        }

        @Override
        public String name() {
            return "example-string-agent";
        }
    }

    static class ExampleStringModel implements Model {
        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            return CompletableFuture.supplyAsync(() -> {
                final var output = "All systems green and stable";
                final var usage = new ModelUsageStats();

                final var data = JsonNodeFactory.instance.objectNode();
                data.put(Agent.OUTPUT_VARIABLE_NAME, output);

                final var text = new Text(context.getSessionId(),
                                          context.getRunId(),
                                          output,
                                          usage,
                                          0);

                final var allMessages = new ArrayList<>(oldMessages);
                allMessages.add(new ToolCall(context.getSessionId(),
                                             context.getRunId(),
                                             "tc-1",
                                             "fetch_weather",
                                             "{\"city\":\"blr\"}"));
                allMessages.add(new ToolCall(context.getSessionId(),
                                             context.getRunId(),
                                             "tc-2",
                                             "fetch_calculator",
                                             "{\"expression\":\"2+2\"}"));
                allMessages.add(new ToolCall(context.getSessionId(),
                                             context.getRunId(),
                                             "tc-3",
                                             "fetch_weather",
                                             "{\"city\":\"blr\"}"));
                allMessages.add(text);

                return ModelOutput.success(data,
                                           List.of(text),
                                           allMessages,
                                           usage);
            });
        }
    }

    static class FixedEmbeddingModel implements EmbeddingModel {
        private final Map<String, float[]> embeddings;

        FixedEmbeddingModel(Map<String, float[]> embeddings) {
            this.embeddings = embeddings;
        }

        @Override
        public void close() {
            // no-op for test model
        }

        @Override
        public int dimensions() {
            return 2;
        }

        @Override
        public float[] getEmbedding(String input) {
            return embeddings.getOrDefault(input, new float[]{
                    0f, 0f
            });
        }
    }

    @Test
    void exampleCoversJsonPathExpectationOperators() {
        final var agent = new ExampleDecisionAgent(AgentSetup.builder()
                .mapper(new ObjectMapper())
                .model(new ExampleDecisionModel())
                .build());

        final List<Expectation<DecisionOutput, String>> expectations = List.of(
                                                                               Expectations
                                                                                       .jsonPathEquals(
                                                                                                       "$.status",
                                                                                                       "SUCCESS"),
                                                                               Expectations
                                                                                       .<DecisionOutput, String>where(
                                                                                                                      "$.status")
                                                                                       .eq("SUCCESS"),
                                                                               Expectations
                                                                                       .<DecisionOutput, String>where(
                                                                                                                      "$.status")
                                                                                       .ne("FAILED"),
                                                                               Expectations.<DecisionOutput, String>at(
                                                                                                                       "$.score")
                                                                                       .gt(90),
                                                                               Expectations.<DecisionOutput, String>at(
                                                                                                                       "$.score")
                                                                                       .gte(92),
                                                                               Expectations.<DecisionOutput, String>at(
                                                                                                                       "$.attempts")
                                                                                       .lt(5),
                                                                               Expectations.<DecisionOutput, String>at(
                                                                                                                       "$.attempts")
                                                                                       .lte(3),
                                                                               Expectations.<DecisionOutput, String>at(
                                                                                                                       "$.category")
                                                                                       .in(List.of("GOLD", "PLATINUM")),
                                                                               Expectations.<DecisionOutput, String>at(
                                                                                                                       "$.category")
                                                                                       .notIn(List.of("SILVER",
                                                                                                      "BRONZE")));

        final var dataset = new Dataset<>("example-jsonpath-evals",
                                          List.of(new TestCase<>("decision request",
                                                                 expectations)));

        final var report = new EvalEngine().run(dataset, agent);

        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getPassedTestCases());
        assertEquals(0, report.getFailedTestCases());
        assertEquals(9, report.getTestCaseReports().get(0).getExpectationReports().size());
    }

    @Test
    void exampleCoversStringExpectationsAndMetrics() {
        final var agent = new ExampleStringAgent(AgentSetup.builder()
                .mapper(new ObjectMapper())
                .model(new ExampleStringModel())
                .build());

        final var embeddingModel = new FixedEmbeddingModel(Map.of(
                                                                  "All systems green and stable",
                                                                  new float[]{
                                                                          1f, 0f
                                                                  },
                                                                  "all systems request",
                                                                  new float[]{
                                                                          1f, 0f
                                                                  }));

        final List<Expectation<String, String>> expectations = List.of(
                                                                       Expectations.outputContains("green"),
                                                                       Expectations.outputEquals(
                                                                                                 "All systems green and stable"),
                                                                       Expectations.toolCalled("fetch_calculator"),
                                                                       Expectations.toolCalled("fetch_weather", 2),
                                                                       Expectations.toolCalled("fetch_weather",
                                                                                               2,
                                                                                               Map.of("city", "blr")),
                                                                       Expectations.ordered(Expectations.toolCalled(
                                                                                                                    "fetch_weather"),
                                                                                            Expectations.toolCalled(
                                                                                                                    "fetch_calculator")),
                                                                       Expectations.outputSimilarity(embeddingModel,
                                                                                                     "All systems green and stable",
                                                                                                     0.9),
                                                                       Expectations.answerRelevance(
                                                                                                    new ExampleAnswerRelevanceJudgeModel(),
                                                                                                    0.9),
                                                                       Expectations.outputSimilarity(embeddingModel,
                                                                                                     "All systems green and stable"));

        final var dataset = new Dataset<>("example-string-evals",
                                          List.of(new TestCase<>("all systems request",
                                                                 expectations)));

        final var report = new EvalEngine().run(dataset, agent);

        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getPassedTestCases());
        assertEquals(0, report.getFailedTestCases());

        final var expectationReports = report.getTestCaseReports().get(0).getExpectationReports();
        assertEquals(9, expectationReports.size());
        assertTrue(expectationReports.stream().anyMatch(r -> r.getExpectation().equals("AnswerRelevance")
                && r.getThreshold().isPresent() && r.getScore().isPresent()));
        assertTrue(expectationReports.stream().anyMatch(r -> r.getExpectation().equals("OutputSimilarity")
                && r.getThreshold().isEmpty() && r.getScore().isPresent()));
    }
}
