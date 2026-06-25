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
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;
import com.phonepe.sentinelai.evals.tests.Expectation;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutor;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutorFactory;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutorRegistry;
import com.phonepe.sentinelai.evals.tests.Expectations;
import com.phonepe.sentinelai.evals.tests.TestCase;
import com.phonepe.sentinelai.evals.tests.expectations.AgentEventExpectation;
import com.phonepe.sentinelai.evals.tests.expectations.Operator;
import com.phonepe.sentinelai.evals.tests.expectations.OutputCompareExpectation;
import com.phonepe.sentinelai.evals.tests.metrics.CostCalculator;
import com.phonepe.sentinelai.evals.tests.metrics.EmbeddingModelFactory;
import com.phonepe.sentinelai.evals.tests.metrics.EmbeddingModelIdentifier;
import com.phonepe.sentinelai.evals.tests.metrics.EventCountMetric;
import com.phonepe.sentinelai.evals.tests.metrics.EventLatencyMetric;
import com.phonepe.sentinelai.evals.tests.metrics.LLMIdentifier;
import com.phonepe.sentinelai.evals.tests.metrics.LLMModelFactory;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExpectation;
import com.phonepe.sentinelai.evals.tests.metrics.OutputRelevanceBySimilarityMetric;

import lombok.NonNull;
import lombok.val;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Comprehensive end-to-end test suite for {@link EvalEngine}.
 *
 * <p>Covers every expectation variant, every metric type, and all engine runtime behaviors
 * (fail-fast, sampling, timeouts, evaluate-all, custom registries).
 */
class EvalEngineE2ETest {

    /* ---- Custom expectation for registry test ---- */
    static class CustomExpectation<R, T> extends Expectation<R, T> {
        CustomExpectation() {
            super("custom-expectation");
        }
    }

    /* ---- Test agents ---- */
    static class DecisionAgent extends Agent<AgentInput, DecisionOutput, DecisionAgent> {
        protected DecisionAgent(@NonNull AgentSetup setup) {
            super(DecisionOutput.class, "Decision assistant", setup, List.of(), Map.of());
        }

        @Override
        public String name() {
            return "decision-agent";
        }
    }

    /* ---- Mock models ---- */
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
                final var usage = new ModelUsageStats();
                usage.incrementRequestTokens(100);
                usage.incrementResponseTokens(50);

                final var outputNode = JsonNodeFactory.instance.objectNode();
                outputNode.put("status", "SUCCESS");
                outputNode.put("score", 85);
                outputNode.put("attempts", 3);
                outputNode.put("category", "GOLD");
                final var tagsNode = outputNode.putArray("tags");
                tagsNode.add("fast");
                tagsNode.add("reliable");

                final var data = JsonNodeFactory.instance.objectNode();
                data.set(Agent.OUTPUT_VARIABLE_NAME, outputNode);

                final var text = new Text(context.getSessionId(),
                                          context.getRunId(),
                                          "Decision generated",
                                          usage,
                                          0);

                final var allMessages = new ArrayList<>(oldMessages);
                allMessages.add(text);

                return ModelOutput.success(data, List.of(text), allMessages, usage);
            });
        }

        @Override
        public String modelName() {
            return "decision-model";
        }
    }

    static class FixedEmbeddingModel implements EmbeddingModel {
        @Override
        public void close() {
            // no-op
        }

        @Override
        public int dimensions() {
            return 2;
        }

        @Override
        public float[] getEmbedding(String input) {
            if (input == null || input.isBlank()) {
                return new float[]{
                        0f, 0f
                };
            }
            final var normalized = input.toLowerCase();
            if (normalized.contains("hello world")) {
                return new float[]{
                        1f, 0f
                };
            }
            if (normalized.contains("hello") || normalized.contains("world")) {
                return new float[]{
                        0.9f, 0.1f
                };
            }
            return new float[]{
                    0f, 0f
            };
        }
    }

    static class MockJudgeModel implements Model {
        private final double score;

        MockJudgeModel(double score) {
            this.score = score;
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
                final var data = JsonNodeFactory.instance.objectNode();
                data.put(Agent.OUTPUT_VARIABLE_NAME,
                         "{\"score\":" + score + ",\"reason\":\"mock judge\"}");
                final var safeMessages = Objects.requireNonNullElse(oldMessages, List.<AgentMessage>of());
                return ModelOutput.success(data, List.of(), safeMessages, new ModelUsageStats());
            });
        }

        @Override
        public String modelName() {
            return "mock-judge";
        }
    }

    static class StringAgent extends Agent<AgentInput, String, StringAgent> {
        protected StringAgent(@NonNull AgentSetup setup) {
            super(String.class, "String assistant", setup, List.of(), Map.of());
        }

        @Override
        public String name() {
            return "string-agent";
        }
    }

    static class StringModel implements Model {
        private final String output;

        StringModel(String output) {
            this.output = output;
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
                final var usage = new ModelUsageStats();
                final var data = JsonNodeFactory.instance.objectNode();
                data.put(Agent.OUTPUT_VARIABLE_NAME, output);

                final var text = new Text(context.getSessionId(),
                                          context.getRunId(),
                                          output,
                                          usage,
                                          0);

                final var allMessages = new ArrayList<>(oldMessages);
                allMessages.add(text);

                return ModelOutput.success(data, List.of(text), allMessages, usage);
            });
        }

        @Override
        public String modelName() {
            return "string-model";
        }
    }

    static class ToolAgent extends Agent<AgentInput, String, ToolAgent> {
        protected ToolAgent(@NonNull AgentSetup setup) {
            super(String.class, "Tool assistant", setup, List.of(), Map.of());
        }

        @Override
        public String name() {
            return "tool-agent";
        }
    }

    static class ToolEmittingModel implements Model {
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
                final var output = "All tools executed";
                final var data = JsonNodeFactory.instance.objectNode();
                data.put(Agent.OUTPUT_VARIABLE_NAME, output);

                final var allMessages = new ArrayList<>(oldMessages);
                allMessages.add(new ToolCall(context.getSessionId(),
                                             context.getRunId(),
                                             "tc-1",
                                             "fetch_user",
                                             "{}"));
                allMessages.add(new ToolCall(context.getSessionId(),
                                             context.getRunId(),
                                             "tc-2",
                                             "fetch_account",
                                             "{}"));
                allMessages.add(new ToolCall(context.getSessionId(),
                                             context.getRunId(),
                                             "tc-3",
                                             "fetch_user",
                                             "{}"));
                allMessages.add(new Text(context.getSessionId(),
                                         context.getRunId(),
                                         output,
                                         usage,
                                         0));

                return ModelOutput.success(data,
                                           List.of(allMessages.get(allMessages.size() - 1)),
                                           allMessages,
                                           usage);
            });
        }

        @Override
        public String modelName() {
            return "tool-emitting";
        }
    }

    private static EvalEngine defaultEngine() {
        return new EvalEngine(new ObjectMapper(), ExpectationExecutorRegistry.withDefaults());
    }

    private static EvalEngine engineWithEmbeddingAndJudge(double judgeScore) {
        final var mapper = new ObjectMapper();
        final var embeddingModel = new FixedEmbeddingModel();
        final var judgeModel = new MockJudgeModel(judgeScore);

        final var metricRegistry = MetricExecutorRegistry.withDefaults(
                                                                       new EmbeddingModelIdentifier("fixed-embedding"),
                                                                       (EmbeddingModelFactory) id -> embeddingModel,
                                                                       new LLMIdentifier("judge-model"),
                                                                       (LLMModelFactory) id -> judgeModel,
                                                                       mapper);

        final var expectationRegistry = ExpectationExecutorRegistry.withDefaults(metricRegistry, mapper);
        return new EvalEngine(mapper, expectationRegistry);
    }

    private static EvalEngine engineWithJudgeOnly(double judgeScore) {
        final var mapper = new ObjectMapper();
        final var judgeModel = new MockJudgeModel(judgeScore);

        // Must pass non-null LLMIdentifier so OutputRelevanceMetric gets registered
        final var metricRegistry = MetricExecutorRegistry.withDefaults(
                                                                       null,
                                                                       EmbeddingModelFactory.noOp(),
                                                                       new LLMIdentifier("judge-model"),
                                                                       (LLMModelFactory) id -> judgeModel,
                                                                       mapper);

        final var expectationRegistry = ExpectationExecutorRegistry.withDefaults(metricRegistry, mapper);
        return new EvalEngine(mapper, expectationRegistry);
    }

    /* ---- Builder helpers ---- */
    private static AgentSetup setup(Model model) {
        return AgentSetup.builder()
                .mapper(new ObjectMapper())
                .model(model)
                .build();
    }

    /* ---- DTOs used by agents ---- */
    record AgentInput(String query) {
    }

    record DecisionOutput(
            String status,
            int score,
            int attempts,
            String category,
            List<String> tags
    ) {
    }

    /* ====== EXPECTATION VARIANTS ====== */

    @Test
    void agentEventExpectation() {
        final var tracer = new AgentEventTracer();

        // Pre-seed the tracer with a matching event (event bus delivery is async
        // and non-deterministic in tests).
        tracer.handleAgentEvent(
                                new com.phonepe.sentinelai.core.events.OutputGeneratedAgentEvent(
                                                                                                 "string-agent",
                                                                                                 "run-1",
                                                                                                 null,
                                                                                                 null,
                                                                                                 "ok",
                                                                                                 null,
                                                                                                 java.time.Duration
                                                                                                         .ofMillis(10)));

        final var metricRegistry = MetricExecutorRegistry.withDefaults().withEventMetrics(tracer);
        final var expectationRegistry = ExpectationExecutorRegistry.withDefaults(metricRegistry, new ObjectMapper())
                // Override the default tracer created by withDefaults() with our seeded one
                .withEventExpectations(tracer);
        final var engine = new EvalEngine(new ObjectMapper(), expectationRegistry);

        final var agent = new StringAgent(setup(new StringModel("ok")));

        // Use a null JSON path so the executor just checks event existence (no
        // field extraction needed).
        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           new AgentEventExpectation<>(
                                                                                                       "agentEventExpectation",
                                                                                                       "string-agent",
                                                                                                       com.phonepe.sentinelai.core.events.EventType.OUTPUT_GENERATED,
                                                                                                       null,
                                                                                                       null,
                                                                                                       null,
                                                                                                       null));

        final var dataset = new Dataset<AgentInput, String>("agent-event",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
    }

    @Test
    void combinedExpectations() {
        final var tracer = new AgentEventTracer();
        tracer.handleAgentEvent(new com.phonepe.sentinelai.core.events.ToolCalledAgentEvent(
                                                                                            "tool-agent",
                                                                                            "run-1",
                                                                                            null,
                                                                                            null,
                                                                                            "tc-1",
                                                                                            "fetch_user",
                                                                                            "{}"));
        tracer.handleAgentEvent(new com.phonepe.sentinelai.core.events.ToolCalledAgentEvent(
                                                                                            "tool-agent",
                                                                                            "run-1",
                                                                                            null,
                                                                                            null,
                                                                                            "tc-2",
                                                                                            "fetch_account",
                                                                                            "{}"));
        tracer.handleAgentEvent(new com.phonepe.sentinelai.core.events.ToolCalledAgentEvent(
                                                                                            "tool-agent",
                                                                                            "run-1",
                                                                                            null,
                                                                                            null,
                                                                                            "tc-3",
                                                                                            "fetch_user",
                                                                                            "{}"));

        final var metricRegistry = MetricExecutorRegistry.withDefaults().withEventMetrics(tracer);
        final var expectationRegistry = ExpectationExecutorRegistry.withDefaults(metricRegistry, new ObjectMapper())
                .withEventExpectations(tracer);
        final var engine = new EvalEngine(new ObjectMapper(), expectationRegistry);
        final var agent = new ToolAgent(setup(new ToolEmittingModel()));

        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           Expectations.outputContains(
                                                                                                       "combinedExpectations",
                                                                                                       "tools"),
                                                                           Expectations.outputEquals(
                                                                                                     "combinedExpectations",
                                                                                                     "All tools executed"),
                                                                           new AgentEventExpectation<>(
                                                                                                       "combinedExpectations-fetchUser",
                                                                                                       null,
                                                                                                       com.phonepe.sentinelai.core.events.EventType.TOOL_CALLED,
                                                                                                       "fetch_user",
                                                                                                       null,
                                                                                                       null,
                                                                                                       null),
                                                                           new AgentEventExpectation<>(
                                                                                                       "combinedExpectations-fetchAccount",
                                                                                                       null,
                                                                                                       com.phonepe.sentinelai.core.events.EventType.TOOL_CALLED,
                                                                                                       "fetch_account",
                                                                                                       null,
                                                                                                       null,
                                                                                                       null));

        final var dataset = new Dataset<AgentInput, String>("combined",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);

        assertEquals(1, report.getPassedTestCases());
        assertEquals(4, report.getTestCaseReports().get(0).getExpectationReports().size());
        assertTrue(report.getTestCaseReports().get(0).getExpectationReports().stream()
                .allMatch(r -> r.getStatus() == EvalStatus.PASSED));
    }

    @Test
    void costMetric() {
        final var costCalculator = new CostCalculator() {
            @Override
            public double calculate(String modelId, ModelUsageStats usage) {
                return 0.42;
            }
        };
        final var metricRegistry = MetricExecutorRegistry.withDefaults().withCostMetric(costCalculator);
        final var expectationRegistry = ExpectationExecutorRegistry.withDefaults(metricRegistry, new ObjectMapper());
        final var engine = new EvalEngine(new ObjectMapper(), expectationRegistry);

        final var agent = new StringAgent(setup(new StringModel("test")));
        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           new MetricExpectation<>("costMetric",
                                                                                                   new com.phonepe.sentinelai.evals.tests.metrics.CostMetric<>()));

        final var dataset = new Dataset<AgentInput, String>("cost-metric",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
        assertTrue(report.getTestCaseReports().get(0).getExpectationReports().get(0).getScore().isPresent());
        assertEquals(0.42,
                     report.getTestCaseReports().get(0).getExpectationReports().get(0).getScore().get(),
                     0.001);
    }

    @Test
    void customExpectationViaRegistry() {
        final var mapper = new ObjectMapper();
        final var registry = ExpectationExecutorRegistry.withDefaults();

        registry.register((Class) CustomExpectation.class, new ExpectationExecutorFactory() {
            @Override
            public <R, T, A extends Agent<R, T, A>> ExpectationExecutor<R, T> create(
                                                                                     Agent<R, T, A> agent,
                                                                                     Expectation<R, T> expectation,
                                                                                     ObjectMapper objectMapper,
                                                                                     java.util.concurrent.ExecutorService executorService) {
                return new ExpectationExecutor<R, T>() {
                    @Override
                    public boolean evaluate(R result, EvalExpectationContext<T> context) {
                        return "expected".equals(result);
                    }
                };
            }
        });

        final var engine = new EvalEngine(mapper, registry);
        final var agent = new StringAgent(setup(new StringModel("expected")));

        final List<Expectation<String, AgentInput>> passExpectations = List.of(new CustomExpectation<>());
        final var passDataset = new Dataset<AgentInput, String>("custom-pass",
                                                                List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                         passExpectations)));
        final var passReport = engine.run(passDataset, agent);
        assertEquals(1, passReport.getPassedTestCases());

        final var failAgent = new StringAgent(setup(new StringModel("unexpected")));
        final var failDataset = new Dataset<AgentInput, String>("custom-fail",
                                                                List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                         passExpectations)));
        final var failReport = engine.run(failDataset, failAgent);
        assertEquals(1, failReport.getFailedTestCases());
    }

    @Test
    void defaultEngineConstructor() {
        final var engine = new EvalEngine();
        final var agent = new StringAgent(setup(new StringModel("ok")));

        final var dataset = new Dataset<AgentInput, String>("default-engine",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     List.of(Expectations
                                                                                                             .outputEquals("defaultEngineConstructor",
                                                                                                                           "ok")))));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
    }

    @Test
    void engineWithExplicitExecutor() {
        final var engine = new EvalEngine(new ObjectMapper(), java.util.concurrent.ForkJoinPool.commonPool());
        final var agent = new StringAgent(setup(new StringModel("ok")));

        final var dataset = new Dataset<AgentInput, String>("explicit-executor",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     List.of(Expectations
                                                                                                             .outputEquals("engineWithExplicitExecutor",
                                                                                                                           "ok")))));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
    }

    @Test
    void evaluateAllExpectations() {
        final var engine = defaultEngine();
        final var agent = new StringAgent(setup(new StringModel("ok")));

        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           Expectations.outputContains(
                                                                                                       "eval-all-1",
                                                                                                       "missing"),
                                                                           Expectations.outputContains(
                                                                                                       "eval-all-2",
                                                                                                       "ok"),
                                                                           Expectations.outputEquals(
                                                                                                     "eval-all-3",
                                                                                                     "ok"));

        final var dataset = new Dataset<AgentInput, String>("evaluate-all",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent, EvalRunConfig.defaults());

        assertEquals(1, report.getFailedTestCases());
        assertEquals(3, report.getTestCaseReports().get(0).getExpectationReports().size());

        final var statuses = report.getTestCaseReports().get(0).getExpectationReports().stream()
                .map(ExpectationReport::getStatus)
                .toList();
        assertTrue(statuses.contains(EvalStatus.FAILED));
        assertTrue(statuses.contains(EvalStatus.PASSED));
    }

    @Test
    void eventCountAndEventLatencyMetrics() {
        final var tracer = new AgentEventTracer();
        final var metricRegistry = MetricExecutorRegistry.withDefaults().withEventMetrics(tracer);
        final var expectationRegistry = ExpectationExecutorRegistry.withDefaults(metricRegistry, new ObjectMapper())
                .withEventExpectations(tracer);
        final var engine = new EvalEngine(new ObjectMapper(), expectationRegistry);

        final var agent = new StringAgent(setup(new StringModel("ok")));
        agent.getSetup().getEventBus().onEvent().connect(tracer::handleAgentEvent);

        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           new MetricExpectation<>("eventCountAndEventLatencyMetrics-count",
                                                                                                   new EventCountMetric<>(
                                                                                                                          "string-agent",
                                                                                                                          com.phonepe.sentinelai.core.events.EventType.OUTPUT_GENERATED,
                                                                                                                          null)),
                                                                           new MetricExpectation<>("eventCountAndEventLatencyMetrics-latency",
                                                                                                   new EventLatencyMetric<>(
                                                                                                                            "string-agent",
                                                                                                                            com.phonepe.sentinelai.core.events.EventType.OUTPUT_GENERATED,
                                                                                                                            null)));

        final var dataset = new Dataset<AgentInput, String>("event-metrics",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
        assertEquals(2, report.getTestCaseReports().get(0).getExpectationReports().size());
    }

    @Test
    void failFastStopsAfterFirstFailure() {
        final var engine = defaultEngine();
        final var agent = new StringAgent(setup(new StringModel("ok")));

        final List<TestCase<AgentInput, String>> tests = List.of(
                                                                 new TestCase<AgentInput, String>(new AgentInput("first"),
                                                                                                  List.of(Expectations
                                                                                                          .outputContains("failFastStopsAfterFirstFailure",
                                                                                                                          "missing"))),
                                                                 new TestCase<AgentInput, String>(new AgentInput("second"),
                                                                                                  List.of(Expectations
                                                                                                          .outputContains("failFastStopsAfterFirstFailure",
                                                                                                                          "ok"))));
        final var dataset = new Dataset<AgentInput, String>("fail-fast-dataset", tests);

        final var report = engine.run(dataset,
                                      agent,
                                      EvalRunConfig.defaults().withFailFast(true));

        assertEquals(2, report.getSampledTestCases());
        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getFailedTestCases());
        assertFalse(report.isCompletedAllSampledCases());
    }

    /* ====== METRIC EXPECTATIONS ====== */

    @Test
    void jsonPathCompareExpectationAllOperators() {
        final var agent = new DecisionAgent(setup(new DecisionModel()));

        final List<Expectation<DecisionOutput, AgentInput>> expectations = List.of(
                                                                                   Expectations.jsonPathEquals(
                                                                                                               "jsonpath-eq-status",
                                                                                                               "$.status",
                                                                                                               "SUCCESS"),
                                                                                   Expectations
                                                                                           .<DecisionOutput, AgentInput>where("where-eq-status",
                                                                                                                              "$.status")
                                                                                           .eq("SUCCESS"),
                                                                                   Expectations
                                                                                           .<DecisionOutput, AgentInput>where("where-ne-status",
                                                                                                                              "$.status")
                                                                                           .ne("FAILED"),
                                                                                   Expectations
                                                                                           .<DecisionOutput, AgentInput>at("at-gt-score",
                                                                                                                           "$.score")
                                                                                           .gt(80),
                                                                                   Expectations
                                                                                           .<DecisionOutput, AgentInput>at("at-gte-score",
                                                                                                                           "$.score")
                                                                                           .gte(85),
                                                                                   Expectations
                                                                                           .<DecisionOutput, AgentInput>at("at-lt-score",
                                                                                                                           "$.score")
                                                                                           .lt(90),
                                                                                   Expectations
                                                                                           .<DecisionOutput, AgentInput>at("at-lte-score",
                                                                                                                           "$.score")
                                                                                           .lte(85),
                                                                                   Expectations
                                                                                           .<DecisionOutput, AgentInput>at("at-in-category",
                                                                                                                           "$.category")
                                                                                           .in(List.of("GOLD",
                                                                                                       "PLATINUM")),
                                                                                   Expectations
                                                                                           .<DecisionOutput, AgentInput>at("at-notIn-category",
                                                                                                                           "$.category")
                                                                                           .notIn(List.of("SILVER",
                                                                                                          "BRONZE")));

        final var dataset = new Dataset<AgentInput, DecisionOutput>("jsonpath-all-ops",
                                                                    List.of(new TestCase<AgentInput, DecisionOutput>(new AgentInput("test"),
                                                                                                                     expectations)));
        final var report = defaultEngine().run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
        assertEquals(9, report.getTestCaseReports().get(0).getExpectationReports().size());
        assertTrue(report.getTestCaseReports().get(0).getExpectationReports().stream()
                .allMatch(r -> r.getStatus() == EvalStatus.PASSED));
    }

    @Test
    void metricExpectationThresholdFailure() {
        final var engine = engineWithJudgeOnly(0.3);
        final var agent = new StringAgent(setup(new StringModel("test output")));

        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           Expectations.answerRelevance(
                                                                                                        "metricExpectationThresholdFailure",
                                                                                                        0.9));

        final var dataset = new Dataset<AgentInput, String>("threshold-fail",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getFailedTestCases());
        assertEquals(1, report.getTestCaseReports().get(0).getExpectationReports().size());
        assertTrue(report.getTestCaseReports().get(0).getExpectationReports().get(0).getScore().isPresent());
        assertTrue(report.getTestCaseReports().get(0).getExpectationReports().get(0).getThreshold().isPresent());
    }

    @Test
    void outputCompareExpectationAllOperators() {
        final var agent = new StringAgent(setup(new StringModel("42")));

        // All expected values are Strings to avoid type-mismatch in Operator.compareOrder
        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           new OutputCompareExpectation<>("outputCompareExpectationAllOperators-eq",
                                                                                                          "42",
                                                                                                          Operator.EQ),
                                                                           new OutputCompareExpectation<>("outputCompareExpectationAllOperators-ne",
                                                                                                          "99",
                                                                                                          Operator.NE),
                                                                           new OutputCompareExpectation<>("outputCompareExpectationAllOperators-gt",
                                                                                                          "40",
                                                                                                          Operator.GT),
                                                                           new OutputCompareExpectation<>("outputCompareExpectationAllOperators-gte",
                                                                                                          "42",
                                                                                                          Operator.GTE),
                                                                           new OutputCompareExpectation<>("outputCompareExpectationAllOperators-lt",
                                                                                                          "50",
                                                                                                          Operator.LT),
                                                                           new OutputCompareExpectation<>("outputCompareExpectationAllOperators-lte",
                                                                                                          "42",
                                                                                                          Operator.LTE),
                                                                           new OutputCompareExpectation<>("outputCompareExpectationAllOperators-in",
                                                                                                          List.of("42",
                                                                                                                  "99"),
                                                                                                          Operator.IN),
                                                                           new OutputCompareExpectation<>("outputCompareExpectationAllOperators-notIn",
                                                                                                          List.of(
                                                                                                                  "wrong",
                                                                                                                  "also wrong"),
                                                                                                          Operator.NOT_IN));

        final var dataset = new Dataset<AgentInput, String>("compare-all-ops",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = defaultEngine().run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
        assertEquals(8, report.getTestCaseReports().get(0).getExpectationReports().size());
        assertTrue(report.getTestCaseReports().get(0).getExpectationReports().stream()
                .allMatch(r -> r.getStatus() == EvalStatus.PASSED));
    }

    @Test
    void outputContainsExpectation() {
        final var agent = new StringAgent(setup(new StringModel("Hello World")));

        final List<Expectation<String, AgentInput>> passExpectations = List.of(
                                                                               Expectations.outputContains(
                                                                                                           "outputContainsExpectation",
                                                                                                           "Hello"),
                                                                               Expectations.outputContains(
                                                                                                           "outputContainsExpectation",
                                                                                                           "World"),
                                                                               Expectations.outputContains(
                                                                                                           "outputContainsExpectation",
                                                                                                           "hello"));
        final var passDataset = new Dataset<AgentInput, String>("contains-pass",
                                                                List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                         passExpectations)));
        final var passReport = defaultEngine().run(passDataset, agent);
        assertEquals(1, passReport.getPassedTestCases());
        assertEquals(3, passReport.getTestCaseReports().get(0).getExpectationReports().size());
        assertTrue(passReport.getTestCaseReports().get(0).getExpectationReports().stream()
                .allMatch(r -> r.getStatus() == EvalStatus.PASSED));

        final List<Expectation<String, AgentInput>> failExpectations = List.of(
                                                                               Expectations.outputContains(
                                                                                                           "outputContainsExpectation",
                                                                                                           "missing"));
        final var failDataset = new Dataset<AgentInput, String>("contains-fail",
                                                                List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                         failExpectations)));
        final var failReport = defaultEngine().run(failDataset, agent);
        assertEquals(1, failReport.getFailedTestCases());
        assertTrue(failReport.getTestCaseReports().get(0).getExpectationReports().stream()
                .allMatch(r -> r.getStatus() == EvalStatus.FAILED));
    }

    @Test
    void outputEqualsExpectation() {
        final var agent = new StringAgent(setup(new StringModel("Exact Match")));

        final var passDataset = new Dataset<AgentInput, String>("equals-pass",
                                                                List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                         List.of(Expectations
                                                                                                                 .outputEquals("outputEqualsExpectation",
                                                                                                                               "Exact Match")))));
        final var passReport = defaultEngine().run(passDataset, agent);
        assertEquals(1, passReport.getPassedTestCases());

        final var failDataset = new Dataset<AgentInput, String>("equals-fail",
                                                                List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                         List.of(Expectations
                                                                                                                 .outputEquals("outputEqualsExpectation",
                                                                                                                               "Wrong")))));
        final var failReport = defaultEngine().run(failDataset, agent);
        assertEquals(1, failReport.getFailedTestCases());
    }

    /* ====== ENGINE BEHAVIORS ====== */

    @Test
    void outputRelevanceBySimilarityMetric() {
        final var engine = engineWithEmbeddingAndJudge(0.0);
        final var agent = new StringAgent(setup(new StringModel("Hello World")));

        final var metric = new OutputRelevanceBySimilarityMetric<AgentInput>();
        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           new MetricExpectation<>("outputRelevanceBySimilarityMetric",
                                                                                                   metric,
                                                                                                   0.5));

        final var dataset = new Dataset<AgentInput, String>("relevance-by-similarity",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("hello world"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
    }

    @Test
    void outputRelevanceMetric() {
        final var engine = engineWithJudgeOnly(0.95);
        final var agent = new StringAgent(setup(new StringModel("test output")));

        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           Expectations.answerRelevance(
                                                                                                        "outputRelevanceMetric",
                                                                                                        0.9));

        final var dataset = new Dataset<AgentInput, String>("relevance-metric",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
        assertEquals(1, report.getTestCaseReports().get(0).getExpectationReports().size());
        assertTrue(report.getTestCaseReports().get(0).getExpectationReports().get(0).getScore().isPresent());
    }

    @Test
    void outputSimilarityMetric() {
        final var engine = engineWithEmbeddingAndJudge(0.0);
        final var agent = new StringAgent(setup(new StringModel("Hello World")));

        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           Expectations.outputSimilarity(
                                                                                                         "outputSimilarityMetric-threshold",
                                                                                                         "Hello World",
                                                                                                         0.8),
                                                                           Expectations.outputSimilarity(
                                                                                                         "outputSimilarityMetric",
                                                                                                         "Hello World"));

        final var dataset = new Dataset<AgentInput, String>("similarity-metric",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
        assertEquals(2, report.getTestCaseReports().get(0).getExpectationReports().size());

        final var expectationReports = report.getTestCaseReports().get(0).getExpectationReports();
        assertTrue(expectationReports.stream().anyMatch(r -> r.getThreshold().isPresent()));
        assertTrue(expectationReports.stream().anyMatch(r -> r.getThreshold().isEmpty()));
    }

    @Test
    void reportStructureValidation() {
        final var engine = defaultEngine();
        final var agent = new StringAgent(setup(new StringModel("ok")));

        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           Expectations.outputContains(
                                                                                                       "reportStructureValidation",
                                                                                                       "ok"),
                                                                           Expectations.outputEquals(
                                                                                                     "reportStructureValidation",
                                                                                                     "ok"),
                                                                           new MetricExpectation<>("TokenUsage",
                                                                                                   new com.phonepe.sentinelai.evals.tests.metrics.TokenUsageMetric<>()));

        final var dataset = new Dataset<AgentInput, String>("report-structure",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);

        assertEquals("report-structure", report.getDatasetName());
        assertEquals(1, report.getTotalTestCases());
        assertEquals(1, report.getSampledTestCases());
        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getPassedTestCases());
        assertEquals(0, report.getFailedTestCases());
        assertEquals(0, report.getSkippedTestCases());
        assertTrue(report.isCompletedAllSampledCases());
        assertTrue(report.getDurationMs() >= 0);
        assertNotNull(report.getMetricScores());
        assertEquals(1, report.getTestCaseReports().size());

        final var tcr = report.getTestCaseReports().get(0);
        assertEquals(new AgentInput("test"), tcr.getInput());
        assertEquals(EvalStatus.PASSED, tcr.getStatus());
        assertEquals("ok", tcr.getOutput());
        assertEquals(3, tcr.getExpectationReports().size());
        assertEquals("All expectations passed", tcr.getDetails());
        assertTrue(tcr.getEvalDurationMs() >= 0);
        assertTrue(tcr.getAgentLatencyMs() != null && tcr.getAgentLatencyMs() >= 0);

        assertTrue(report.getMetricScores().stream()
                .anyMatch(ms -> "TokenUsage".equals(ms.getMetricName())));
    }

    @Test
    void samplingReducesExecutedCases() {
        final var engine = defaultEngine();
        final var agent = new StringAgent(setup(new StringModel("ok")));

        final var tests = new ArrayList<TestCase<AgentInput, String>>();
        for (int i = 0; i < 20; i++) {
            tests.add(new TestCase<AgentInput, String>(new AgentInput("input-" + i),
                                                       List.of(Expectations.outputContains(
                                                                                           "samplingReducesExecutedCases",
                                                                                           "ok"))));
        }
        final var dataset = new Dataset<AgentInput, String>("sampling-dataset", tests);

        final var report = engine.run(dataset,
                                      agent,
                                      EvalRunConfig.defaults()
                                              .withSamplePercentage(25)
                                              .withSampleSeed(42L));

        assertEquals(20, report.getTotalTestCases());
        assertEquals(5, report.getSampledTestCases());
        assertEquals(5, report.getExecutedTestCases());
        assertTrue(report.isCompletedAllSampledCases());
    }

    @Test
    void testCaseTimeout() throws Exception {
        final var engine = defaultEngine();
        final var agent = new StringAgent(setup(new StringModel("ok")));

        final List<TestCase<AgentInput, String>> tests = List.of(
                                                                 new TestCase<AgentInput, String>(new AgentInput("slow"),
                                                                                                  List.of(Expectations
                                                                                                          .outputContains("testCaseTimeout",
                                                                                                                          "ok")),
                                                                                                  java.time.Duration
                                                                                                          .ofMillis(1)));
        final var dataset = new Dataset<AgentInput, String>("timeout-dataset", tests);

        final var report = engine.run(dataset, agent, EvalRunConfig.defaults());

        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getSkippedTestCases());
        assertEquals(EvalStatus.SKIPPED, report.getTestCaseReports().get(0).getStatus());
        assertTrue(report.getTestCaseReports().get(0).getDetails().contains("Timed out"));
    }

    @Test
    void tokenUsageAndAgentLatencyMetrics() {
        final var engine = defaultEngine();
        final var agent = new StringAgent(setup(new StringModel("test output")));

        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           new MetricExpectation<>("tokenUsageAndAgentLatencyMetrics-tokenUsage",
                                                                                                   new com.phonepe.sentinelai.evals.tests.metrics.TokenUsageMetric<>()),
                                                                           new MetricExpectation<>("tokenUsageAndAgentLatencyMetrics-agentLatency",
                                                                                                   new com.phonepe.sentinelai.evals.tests.metrics.AgentLatencyMetric<>()));

        final var dataset = new Dataset<AgentInput, String>("token-latency-metrics",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
        assertEquals(2, report.getTestCaseReports().get(0).getExpectationReports().size());

        for (val expectationReport : report.getTestCaseReports().get(0).getExpectationReports()) {
            assertTrue(expectationReport.getScore().isPresent(),
                       "Metric " + expectationReport.getExpectation() + " should have a score");
            assertTrue(expectationReport.getScore().get() >= 0,
                       "Score should be non-negative");
        }
    }

    @Test
    void toolCalledExpectation() {
        final var tracer = new AgentEventTracer();
        // Tool history: fetch_user (1), fetch_account (2), fetch_user (3)
        tracer.handleAgentEvent(new com.phonepe.sentinelai.core.events.ToolCalledAgentEvent(
                                                                                            "tool-agent",
                                                                                            "run-1",
                                                                                            null,
                                                                                            null,
                                                                                            "tc-1",
                                                                                            "fetch_user",
                                                                                            "{}"));
        tracer.handleAgentEvent(new com.phonepe.sentinelai.core.events.ToolCalledAgentEvent(
                                                                                            "tool-agent",
                                                                                            "run-1",
                                                                                            null,
                                                                                            null,
                                                                                            "tc-2",
                                                                                            "fetch_account",
                                                                                            "{}"));
        tracer.handleAgentEvent(new com.phonepe.sentinelai.core.events.ToolCalledAgentEvent(
                                                                                            "tool-agent",
                                                                                            "run-1",
                                                                                            null,
                                                                                            null,
                                                                                            "tc-3",
                                                                                            "fetch_user",
                                                                                            "{}"));

        final var metricRegistry = MetricExecutorRegistry.withDefaults().withEventMetrics(tracer);
        final var expectationRegistry = ExpectationExecutorRegistry.withDefaults(metricRegistry, new ObjectMapper())
                .withEventExpectations(tracer);
        final var engine = new EvalEngine(new ObjectMapper(), expectationRegistry);
        final var agent = new ToolAgent(setup(new ToolEmittingModel()));

        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           new AgentEventExpectation<>(
                                                                                                       "toolCalledExpectation-fetchAccount",
                                                                                                       null,
                                                                                                       com.phonepe.sentinelai.core.events.EventType.TOOL_CALLED,
                                                                                                       "fetch_account",
                                                                                                       null,
                                                                                                       null,
                                                                                                       null),
                                                                           new AgentEventExpectation<>(
                                                                                                       "toolCalledExpectation-fetchUser",
                                                                                                       null,
                                                                                                       com.phonepe.sentinelai.core.events.EventType.TOOL_CALLED,
                                                                                                       "fetch_user",
                                                                                                       null,
                                                                                                       null,
                                                                                                       null));

        final var dataset = new Dataset<AgentInput, String>("tool-called",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getPassedTestCases());
        assertEquals(2, report.getTestCaseReports().get(0).getExpectationReports().size());
        assertTrue(report.getTestCaseReports().get(0).getExpectationReports().stream()
                .allMatch(r -> r.getStatus() == EvalStatus.PASSED));
    }

    @Test
    void toolCalledExpectationFailures() {
        final var tracer = new AgentEventTracer();
        // Only fetch_user and fetch_account exist — nonexistent_tool never fired
        tracer.handleAgentEvent(new com.phonepe.sentinelai.core.events.ToolCalledAgentEvent(
                                                                                            "tool-agent",
                                                                                            "run-1",
                                                                                            null,
                                                                                            null,
                                                                                            "tc-1",
                                                                                            "fetch_user",
                                                                                            "{}"));
        tracer.handleAgentEvent(new com.phonepe.sentinelai.core.events.ToolCalledAgentEvent(
                                                                                            "tool-agent",
                                                                                            "run-1",
                                                                                            null,
                                                                                            null,
                                                                                            "tc-2",
                                                                                            "fetch_account",
                                                                                            "{}"));

        final var metricRegistry = MetricExecutorRegistry.withDefaults().withEventMetrics(tracer);
        final var expectationRegistry = ExpectationExecutorRegistry.withDefaults(metricRegistry, new ObjectMapper())
                .withEventExpectations(tracer);
        final var engine = new EvalEngine(new ObjectMapper(), expectationRegistry);
        final var agent = new ToolAgent(setup(new ToolEmittingModel()));

        final List<Expectation<String, AgentInput>> expectations = List.of(
                                                                           new AgentEventExpectation<>(
                                                                                                       "toolCalledExpectationFailures-nonexistentTool",
                                                                                                       null,
                                                                                                       com.phonepe.sentinelai.core.events.EventType.TOOL_CALLED,
                                                                                                       "nonexistent_tool",
                                                                                                       null,
                                                                                                       null,
                                                                                                       null));

        final var dataset = new Dataset<AgentInput, String>("tool-called-fail",
                                                            List.of(new TestCase<AgentInput, String>(new AgentInput("test"),
                                                                                                     expectations)));
        final var report = engine.run(dataset, agent);
        assertEquals(1, report.getFailedTestCases());
        assertEquals(1, report.getTestCaseReports().get(0).getExpectationReports().size());
    }
}
