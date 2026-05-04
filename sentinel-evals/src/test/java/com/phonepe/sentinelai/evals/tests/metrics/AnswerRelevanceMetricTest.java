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

package com.phonepe.sentinelai.evals.tests.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import org.junit.jupiter.api.Test;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.ExecutableTool;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerRelevanceMetricTest {
    private static final double EPSILON = 1e-6;

    private static class TestEvaluatorModel implements Model {
        private final JsonNode output;
        private final ErrorType errorType;
        private final boolean throwException;

        private ModelRunContext capturedContext;
        private List<AgentMessage> capturedMessages;

        private TestEvaluatorModel(JsonNode output, ErrorType errorType, boolean throwException) {
            this.output = output;
            this.errorType = errorType;
            this.throwException = throwException;
        }

        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            capturedContext = context;
            capturedMessages = oldMessages;

            if (throwException) {
                return CompletableFuture.failedFuture(new RuntimeException("judge failure"));
            }

            final var usage = new ModelUsageStats();
            if (errorType != ErrorType.SUCCESS) {
                return CompletableFuture.completedFuture(ModelOutput.error(List.of(),
                                                                           oldMessages,
                                                                           usage,
                                                                           SentinelError.error(errorType)));
            }

            final var data = JsonNodeFactory.instance.objectNode();
            if (output != null) {
                data.set(Agent.OUTPUT_VARIABLE_NAME, output);
            }
            return CompletableFuture.completedFuture(ModelOutput.success(data,
                                                                         List.of(),
                                                                         oldMessages,
                                                                         usage));
        }
    }

    private static EvalExpectationContext<String> context(String request) {
        return new EvalExpectationContext<>("run-id", request, List.of(), new ModelUsageStats());
    }

    @Test
    void calculateReturnsScoreForValidJsonObjectPayload() {
        final var payload = JsonNodeFactory.instance.objectNode();
        payload.put("score", 0.78);
        payload.put("reason", "Partially aligned");
        final var evaluator = new TestEvaluatorModel(payload, ErrorType.SUCCESS, false);
        final var metric = new OutputRelevanceMetric<String>(evaluator);

        final var score = metric.calculate("Result", context("Request"));

        assertEquals(0.78, score, EPSILON);
    }

    @Test
    void calculateReturnsScoreForValidJsonStringPayload() {
        final var evaluator = new TestEvaluatorModel(JsonNodeFactory.instance.textNode(
                                                                                       "{\"score\":0.91,\"reason\":\"Aligned\"}"),
                                                     ErrorType.SUCCESS,
                                                     false);
        final var metric = new OutputRelevanceMetric<String>(evaluator);

        final var score = metric.calculate("All systems green and stable", context("all systems request"));

        assertEquals(0.91, score, EPSILON);
        assertEquals("AnswerRelevance", metric.metricName());
    }

    @Test
    void calculateReturnsZeroForInvalidJudgePayloads() {
        final var metric = new OutputRelevanceMetric<String>(new TestEvaluatorModel(JsonNodeFactory.instance.textNode(
                                                                                                                      "{}"),
                                                                                    ErrorType.SUCCESS,
                                                                                    false));
        assertEquals(0.0, metric.calculate("answer", context("request")), EPSILON);

        final var missingReason = JsonNodeFactory.instance.objectNode();
        missingReason.put("score", 0.4);
        assertEquals(0.0,
                     new OutputRelevanceMetric<String>(new TestEvaluatorModel(missingReason,
                                                                              ErrorType.SUCCESS,
                                                                              false)).calculate("answer",
                                                                                                context("request")),
                     EPSILON);

        final var outOfRangeScore = JsonNodeFactory.instance.objectNode();
        outOfRangeScore.put("score", 1.1);
        outOfRangeScore.put("reason", "too high");
        assertEquals(0.0,
                     new OutputRelevanceMetric<String>(new TestEvaluatorModel(outOfRangeScore,
                                                                              ErrorType.SUCCESS,
                                                                              false)).calculate("answer",
                                                                                                context("request")),
                     EPSILON);

        final var extraField = JsonNodeFactory.instance.objectNode();
        extraField.put("score", 0.8);
        extraField.put("reason", "ok");
        extraField.put("extra", "not allowed");
        assertEquals(0.0,
                     new OutputRelevanceMetric<String>(new TestEvaluatorModel(extraField,
                                                                              ErrorType.SUCCESS,
                                                                              false)).calculate("answer",
                                                                                                context("request")),
                     EPSILON);
    }

    @Test
    void calculateReturnsZeroForNullOrBlankInputs() {
        final var evaluator = new TestEvaluatorModel(JsonNodeFactory.instance.textNode(
                                                                                       "{\"score\":1.0,\"reason\":\"ok\"}"),
                                                     ErrorType.SUCCESS,
                                                     false);
        final var metric = new OutputRelevanceMetric<String>(evaluator);

        assertEquals(0.0, metric.calculate(null, context("request")), EPSILON);
        assertEquals(0.0, metric.calculate("", context("request")), EPSILON);
        assertEquals(0.0, metric.calculate("answer", null), EPSILON);
        assertEquals(0.0, metric.calculate("answer", context(null)), EPSILON);
        assertEquals(0.0, metric.calculate("answer", context("")), EPSILON);
    }

    @Test
    void calculateReturnsZeroWhenModelErrorsOrThrows() {
        final var erroredMetric = new OutputRelevanceMetric<String>(new TestEvaluatorModel(null,
                                                                                           ErrorType.NO_RESPONSE,
                                                                                           false));
        final var throwingMetric = new OutputRelevanceMetric<String>(new TestEvaluatorModel(null,
                                                                                            ErrorType.SUCCESS,
                                                                                            true));

        assertEquals(0.0, erroredMetric.calculate("answer", context("request")), EPSILON);
        assertEquals(0.0, throwingMetric.calculate("answer", context("request")), EPSILON);
    }

    @Test
    void calculateWiresPromptAndRunContextToJudgeModel() {
        final var evaluator = new TestEvaluatorModel(JsonNodeFactory.instance.textNode(
                                                                                       "{\"score\":0.67,\"reason\":\"ok\"}"),
                                                     ErrorType.SUCCESS,
                                                     false);
        final var metric = new OutputRelevanceMetric<String>(evaluator,
                                                             "REQ={request};ANS={answer}",
                                                             new ObjectMapper());

        final var score = metric.calculate("agent-answer", context("user-request"));

        assertEquals(0.67, score, EPSILON);
        assertEquals("OutputRelevanceMetric", evaluator.capturedContext.getAgentName());
        assertEquals("run-id", evaluator.capturedContext.getRunId());
        assertEquals(2, evaluator.capturedMessages.size());
        final var userPrompt = (UserPrompt) evaluator.capturedMessages.get(1);
        assertTrue(userPrompt.getContent().contains("REQ=user-request;ANS=agent-answer"));
    }

    @Test
    void constructorValidatesInputs() {
        final var evaluator = new TestEvaluatorModel(JsonNodeFactory.instance.textNode(
                                                                                       "{\"score\":0.8,\"reason\":\"ok\"}"),
                                                     ErrorType.SUCCESS,
                                                     false);

        assertThrows(IllegalArgumentException.class, () -> new OutputRelevanceMetric<String>(null));
        assertThrows(IllegalArgumentException.class, () -> new OutputRelevanceMetric<>(evaluator, " "));
        assertThrows(IllegalArgumentException.class,
                     () -> new OutputRelevanceMetric<>(evaluator,
                                                       "Only request: {request}"));
        assertThrows(IllegalArgumentException.class,
                     () -> new OutputRelevanceMetric<>(evaluator,
                                                       "Only answer: {answer}"));
        assertThrows(IllegalArgumentException.class,
                     () -> new OutputRelevanceMetric<>(evaluator,
                                                       "Template without placeholders"));
        assertThrows(IllegalArgumentException.class,
                     () -> new OutputRelevanceMetric<>(evaluator,
                                                       OutputRelevanceMetric.DEFAULT_PROMPT_TEMPLATE,
                                                       null));
    }
}
