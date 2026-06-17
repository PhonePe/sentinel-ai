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

package com.phonepe.sentinelai.evals.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentExtension;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.responses.Text;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.core.tools.Tool;
import com.phonepe.sentinelai.evals.ExpectationReport;
import com.phonepe.sentinelai.evals.tests.metrics.EmbeddingModelFactory;
import com.phonepe.sentinelai.evals.tests.metrics.LLMIdentifier;
import com.phonepe.sentinelai.evals.tests.metrics.LLMModelFactory;
import com.phonepe.sentinelai.evals.tests.metrics.Metric;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorFactory;
import com.phonepe.sentinelai.evals.tests.metrics.MetricExecutorRegistry;

import lombok.NonNull;
import lombok.val;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

/**
 * TestFactory provides reusable builder methods for creating test objects with minimal boilerplate.
 * Centralizes context creation, model instantiation, and dummy agent setup across test suites.
 */
@UtilityClass
public class TestFactory {

    // Default constants
    private static final String DEFAULT_RUN_ID = "test-run";

    /**
     * Generic test agent for use in eval tests. Supports tool execution via {@link Tool} annotations.
     */
    public static class TestAgent extends Agent<String, String, TestAgent> {
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

    /**
     * Test model that returns a configurable string output with optional delay for async testing.
     */
    public static class TestModel implements Model {
        private final String output;

        public TestModel(String output) {
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
                val usage = new ModelUsageStats();
                ObjectNode data = JsonNodeFactory.instance.objectNode();
                data.put(Agent.OUTPUT_VARIABLE_NAME, output);
                val newMessages = List.<AgentMessage>of(new Text(context.getSessionId(),
                                                                 context.getRunId(),
                                                                 output,
                                                                 usage,
                                                                 2));
                val allMessages = new ArrayList<>(oldMessages);
                allMessages.addAll(newMessages);
                return ModelOutput.success(data,
                                           newMessages,
                                           allMessages,
                                           usage);
            });
        }
    }

    /**
     * Creates an AgentSetup builder with sensible defaults (mapper and model).
     *
     * @param model the Model to configure
     * @return AgentSetup.AgentSetupBuilder
     */
    public static AgentSetup.AgentSetupBuilder agentSetupBuilder(Model model) {
        return AgentSetup.builder()
                .mapper(new ObjectMapper())
                .model(model);
    }

    public static <R, T> double calculate(Metric<R, T> metric,
                                          Model answerRelevanceModel,
                                          R result,
                                          EvalExpectationContext<T> context) {
        return metricExecutorFactory(answerRelevanceModel)
                .create(metric, mapper(), ForkJoinPool.commonPool())
                .calculate(result, context);
    }

    public static <R, T> double calculate(Metric<R, T> metric,
                                          R result,
                                          EvalExpectationContext<T> context) {
        return metricExecutorFactory().create(metric, mapper(), ForkJoinPool.commonPool()).calculate(result, context);
    }

    /**
     * Creates an empty {@link EvalExpectationContext} with default run ID, null request, and empty message history.
     * Suitable for simple expectation tests that don't require specific context state.
     */
    public static <T> EvalExpectationContext<T> context() {
        return EvalExpectationContext.<T>builder()
                .runId(DEFAULT_RUN_ID)
                .request(null)
                .oldMessages(List.of())
                .modelUsageStats(new ModelUsageStats())
                .modelId(null)
                .latencyMs(null)
                .build();
    }

    /**
     * Creates an {@link EvalExpectationContext} with the specified message history.
     * Useful for message-related expectations (e.g., ToolCalledExpectation).
     *
     * @param oldMessages the message history to include
     * @return context with the given message history
     */
    public static <T> EvalExpectationContext<T> contextWith(List<AgentMessage> oldMessages) {
        return EvalExpectationContext.<T>builder()
                .runId(DEFAULT_RUN_ID)
                .request(null)
                .oldMessages(oldMessages)
                .modelUsageStats(new ModelUsageStats())
                .modelId(null)
                .latencyMs(null)
                .build();
    }

    /**
     * Creates an {@link EvalExpectationContext} with the specified request object.
     *
     * @param request the request object to include in the context
     * @return context with the given request
     */
    public static <R> EvalExpectationContext<R> contextWith(R request) {
        return EvalExpectationContext.<R>builder()
                .runId(DEFAULT_RUN_ID)
                .request(request)
                .oldMessages(List.of())
                .modelUsageStats(new ModelUsageStats())
                .modelId(null)
                .latencyMs(null)
                .build();
    }

    /**
     * Creates an {@link EvalExpectationContext} with both request and message history.
     *
     * @param request     the request object
     * @param oldMessages the message history
     * @return context with both request and messages
     */
    public static <R> EvalExpectationContext<R> contextWith(R request,
                                                            List<AgentMessage> oldMessages) {
        return EvalExpectationContext.<R>builder()
                .runId(DEFAULT_RUN_ID)
                .request(request)
                .oldMessages(oldMessages)
                .modelUsageStats(new ModelUsageStats())
                .modelId(null)
                .latencyMs(null)
                .build();
    }

    /**
     * Creates an {@link EvalExpectationContext} with custom run ID and all fields.
     *
     * @param runId       the run identifier
     * @param request     the request object
     * @param oldMessages the message history
     * @return context with all specified fields
     */
    public static <R> EvalExpectationContext<R> contextWith(String runId,
                                                            R request,
                                                            List<AgentMessage> oldMessages) {
        return EvalExpectationContext.<R>builder()
                .runId(runId)
                .request(request)
                .oldMessages(oldMessages)
                .modelUsageStats(new ModelUsageStats())
                .modelId(null)
                .latencyMs(null)
                .build();
    }

    /**
     * Creates a default {@link ModelUsageStats} instance (typically empty for test models).
     *
     * @return new ModelUsageStats
     */
    public static ModelUsageStats defaultUsageStats() {
        return new ModelUsageStats();
    }

    public static <R, T> boolean evaluate(Expectation<R, T> expectation,
                                          R result,
                                          EvalExpectationContext<T> context) {
        return expectationExecutorFactory().create(null, expectation, mapper(), ForkJoinPool.commonPool())
                .evaluate(result, context);
    }

    public static <R, T> ExpectationReport evaluateWithReport(Expectation<R, T> expectation,
                                                              Model answerRelevanceModel,
                                                              R result,
                                                              EvalExpectationContext<T> context) {
        return expectationExecutorFactory(metricExecutorFactory(answerRelevanceModel))
                .create(null, expectation, mapper(), ForkJoinPool.commonPool())
                .evaluateWithReport(result, context);
    }

    public static <R, T> ExpectationReport evaluateWithReport(Expectation<R, T> expectation,
                                                              R result,
                                                              EvalExpectationContext<T> context) {
        return expectationExecutorFactory().create(null, expectation, mapper(), ForkJoinPool.commonPool())
                .evaluateWithReport(result, context);
    }

    public static ExpectationExecutorFactory expectationExecutorFactory() {
        return ExpectationExecutorRegistry.withDefaults();
    }

    public static ExpectationExecutorFactory expectationExecutorFactory(MetricExecutorFactory metricExecutorFactory) {
        return ExpectationExecutorRegistry.withDefaults(metricExecutorFactory);
    }

    /**
     * Gets a shared ObjectMapper instance for test setup.
     *
     * @return ObjectMapper
     */
    public static ObjectMapper mapper() {
        return new ObjectMapper();
    }

    public static MetricExecutorFactory metricExecutorFactory() {
        return MetricExecutorRegistry.withDefaults(null,
                                                   EmbeddingModelFactory.noOp(),
                                                   new LLMIdentifier("mock"),
                                                   (LLMModelFactory) id -> (context,
                                                                            outputDefinitions,
                                                                            oldMessages,
                                                                            tools,
                                                                            toolRunner,
                                                                            earlyTerminationStrategy,
                                                                            agentMessagesPreProcessors) -> {
                                                       final var data = JsonNodeFactory.instance.objectNode();
                                                       data.put(Agent.OUTPUT_VARIABLE_NAME,
                                                                "{\"score\":0.0,\"reason\":\"mock\"}");
                                                       final var safeMessages = Objects.requireNonNullElse(oldMessages,
                                                                                                           List.<AgentMessage>of());
                                                       return CompletableFuture.completedFuture(ModelOutput.success(
                                                                                                                    data,
                                                                                                                    List.of(),
                                                                                                                    safeMessages,
                                                                                                                    new ModelUsageStats()));
                                                   });
    }

    public static MetricExecutorFactory metricExecutorFactory(Model answerRelevanceModel) {
        return MetricExecutorRegistry.withDefaults(null, answerRelevanceModel, TestFactory.mapper());
    }

    /**
     * Creates a {@link TestAgent} with the given model.
     *
     * @param model the Model to use
     * @return TestAgent instance
     */
    public static TestAgent testAgent(Model model) {
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

    /**
     * Creates a {@link TestAgent} with a {@link TestModel} that returns the specified output.
     *
     * @param output the string output for the test model
     * @return TestAgent instance
     */
    public static TestAgent testAgent(String output) {
        return testAgent(new TestModel(output));
    }
}
