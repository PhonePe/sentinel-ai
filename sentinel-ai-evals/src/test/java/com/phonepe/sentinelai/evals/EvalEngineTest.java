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
import com.phonepe.sentinelai.evals.tests.Dataset;
import com.phonepe.sentinelai.evals.tests.ExpectationExecutorRegistry;
import com.phonepe.sentinelai.evals.tests.Expectations;
import com.phonepe.sentinelai.evals.tests.TestCase;
import com.phonepe.sentinelai.evals.tests.TestFactory;
import com.phonepe.sentinelai.evals.tests.expectations.ToolCalledExpectation;
import com.phonepe.sentinelai.evals.tests.metrics.Metric;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutor;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorFactory;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExpectation;

import lombok.val;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

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

    private static EvalEngine engineWithMockJudgeModel() {
        final var mapper = TestFactory.mapper();
        final var metricExecutorFactory = MetricExecutorRegistry.withDefaults(null, mockJudgeModel(), mapper);
        final var expectationExecutorFactory = ExpectationExecutorRegistry.withDefaults(metricExecutorFactory,
                                                                                        mapper);
        return new EvalEngine(mapper, expectationExecutorFactory);
    }

    private static Model mockJudgeModel() {
        return (context,
                outputDefinitions,
                oldMessages,
                tools,
                toolRunner,
                earlyTerminationStrategy,
                agentMessagesPreProcessors) -> {
            final var data = JsonNodeFactory.instance.objectNode();
            data.put(Agent.OUTPUT_VARIABLE_NAME, "{\"score\":0.0,\"reason\":\"mock\"}");
            final var safeMessages = oldMessages == null ? List.<AgentMessage>of() : oldMessages;
            return CompletableFuture.completedFuture(ModelOutput.success(data,
                                                                         List.of(),
                                                                         safeMessages,
                                                                         new ModelUsageStats()));
        };
    }

    @Test
    void testFailFast() {
        EvalEngine engine = engineWithMockJudgeModel();
        val tests = List.of(
                            new TestCase<String, String>("first", List.of(Expectations.outputContains("missing"))),
                            new TestCase<String, String>("second", List.of(Expectations.outputContains("ok"))));
        val dataset = new Dataset<String, String>("test-dataset", tests);

        val report = engine.run(dataset,
                                TestFactory.testAgent("ok", 0),
                                EvalRunConfig.defaults().withFailFast(true));

        assertEquals(2, report.getSampledTestCases());
        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getFailedTestCases());
        assertFalse(report.isCompletedAllSampledCases());
    }

    @Test
    void testMetricExpectationSkipMarksTestCaseSkipped() {
        Metric<String, String> unavailableJudgeMetric = new Metric<>() {
            @Override
            public String metricName() {
                return "UnavailableJudgeMetric";
            }
        };
        MetricExecutorFactory metricExecutorFactory = new MetricExecutorFactory() {
            private final MetricExecutorFactory fallback = MetricExecutorRegistry.withDefaults(null,
                                                                                               mockJudgeModel(),
                                                                                               TestFactory.mapper());

            @Override
            public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                                      ObjectMapper objectMapper,
                                                      ExecutorService executorService) {
                if (metric == unavailableJudgeMetric) {
                    return new MetricExecutor<>() {
                        @Override
                        public double calculate(R result,
                                                com.phonepe.sentinelai.evals.tests.EvalExpectationContext<T> context) {
                            throw new RuntimeException("judge unavailable");
                        }

                        @Override
                        public String metricName() {
                            return metric.metricName();
                        }
                    };
                }
                return fallback.create(metric, objectMapper, executorService);
            }
        };
        EvalEngine engine = new EvalEngine(TestFactory.mapper(),
                                           TestFactory.expectationExecutorFactory(metricExecutorFactory));
        val tests = List.of(new TestCase<>("input",
                                           List.of(new MetricExpectation<String, String>(unavailableJudgeMetric,
                                                                                         0.8))));
        val dataset = new Dataset<>("metric-skip-dataset", tests);

        val report = engine.run(dataset,
                                TestFactory.testAgent("ok", 0),
                                EvalRunConfig.defaults());

        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getSkippedTestCases());
        assertEquals(0, report.getFailedTestCases());
        assertEquals(EvalStatus.SKIPPED, report.getTestCaseReports().get(0).getStatus());
    }

    @Test
    void testSampling() {
        EvalEngine engine = engineWithMockJudgeModel();
        val tests = new ArrayList<TestCase<String, String>>();
        for (int i = 0; i < 10; i++) {
            tests.add(new TestCase<>("input-" + i,
                                     List.of(Expectations.outputContains("ok"))));
        }
        val dataset = new Dataset<String, String>("test-dataset", tests);
        val report = engine.run(dataset,
                                TestFactory.testAgent("ok", 0),
                                EvalRunConfig.defaults()
                                        .withSamplePercentage(30)
                                        .withSampleSeed(7L));

        assertEquals(10, report.getTotalTestCases());
        assertEquals(3, report.getSampledTestCases());
        assertEquals(3, report.getExecutedTestCases());
        assertEquals(3, report.getPassedTestCases());
        assertTrue(report.isCompletedAllSampledCases());
    }

    @Test
    void testTimeoutMarksSkipped() {
        EvalEngine engine = engineWithMockJudgeModel();
        val tests = List.of(new TestCase<>("slow",
                                           List.of(Expectations.outputContains("ok")),
                                           Duration.ofMillis(50)));
        val dataset = new Dataset<>("test-dataset", tests);

        val report = engine.run(dataset,
                                TestFactory.testAgent("ok", 200),
                                EvalRunConfig.defaults());

        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getSkippedTestCases());
        assertEquals(EvalStatus.SKIPPED, report.getTestCaseReports().get(0).getStatus());
        assertTrue(report.getTestCaseReports().get(0).getDetails().contains("Timed out"));
    }

    @Test
    void testToolCallsFollowEncodedOrder() {
        EvalEngine engine = engineWithMockJudgeModel();
        val orderedExpectation = Expectations.<String, String>ordered(
                                                                      new ToolCalledExpectation<>("fetch_user"),
                                                                      new ToolCalledExpectation<>("fetch_account"));
        val tests = List.of(new TestCase<String, String>("run tools", List.of(orderedExpectation)));
        val dataset = new Dataset<String, String>("tool-order-dataset", tests);

        val report = engine.run(dataset,
                                TestFactory.testAgent(new OrderedToolCallModel(
                                                                               List.of("test_agent_fetch_user",
                                                                                       "test_agent_fetch_account"),
                                                                               "ok")),
                                EvalRunConfig.defaults());

        assertEquals(1, report.getExecutedTestCases());
        assertEquals(1, report.getPassedTestCases());
        assertEquals(0, report.getFailedTestCases());
    }
}
