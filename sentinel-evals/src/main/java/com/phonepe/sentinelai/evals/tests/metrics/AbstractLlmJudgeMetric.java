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

import org.apache.commons.lang3.StringUtils;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.AgentSetup;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ProcessingMode;
import com.phonepe.sentinelai.core.agentmessages.requests.SystemPrompt;
import com.phonepe.sentinelai.core.agentmessages.requests.UserPrompt;
import com.phonepe.sentinelai.core.earlytermination.NeverTerminateEarlyStrategy;
import com.phonepe.sentinelai.core.errors.ErrorType;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for metrics that use a judge model to evaluate answer quality.
 *
 * Subclasses provide prompt rendering and score extraction while this class handles
 * model invocation, runtime context creation, and defensive fallback behavior.
 *
 * @param <T> input/request type
 */
public abstract class AbstractLlmJudgeMetric<T> implements Metric<String, T> {
    private static final String DEFAULT_AGENT_SESSION = "llm-judge-metric-session";
    private static final String DEFAULT_SYSTEM_INSTRUCTION = "You are an evaluation judge.";

    private final Model evaluatorModel;
    private final String promptTemplate;
    private final ObjectMapper objectMapper;

    protected AbstractLlmJudgeMetric(Model evaluatorModel,
                                     String promptTemplate,
                                     List<String> requiredPlaceholders) {
        this(evaluatorModel, promptTemplate, new ObjectMapper(), requiredPlaceholders);
    }

    protected AbstractLlmJudgeMetric(Model evaluatorModel,
                                     String promptTemplate,
                                     ObjectMapper objectMapper,
                                     List<String> requiredPlaceholders) {
        if (evaluatorModel == null) {
            throw new IllegalArgumentException("evaluatorModel cannot be null");
        }
        if (StringUtils.isBlank(promptTemplate)) {
            throw new IllegalArgumentException("promptTemplate cannot be null or blank");
        }
        if (requiredPlaceholders != null && requiredPlaceholders.stream().anyMatch(ph -> !promptTemplate.contains(
                                                                                                                  ph))) {
            throw new IllegalArgumentException("promptTemplate must contain placeholders: " + requiredPlaceholders);
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper cannot be null");
        }
        this.evaluatorModel = evaluatorModel;
        this.promptTemplate = promptTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public final double calculate(String result, EvalExpectationContext<T> context) {
        if (StringUtils.isBlank(result) || context == null || context.getRequest() == null) {
            return 0.0;
        }
        final var requestText = String.valueOf(context.getRequest());
        if (StringUtils.isBlank(requestText)) {
            return 0.0;
        }

        try {
            final var modelOutput = evaluatorModel.compute(createModelRunContext(context),
                                                           List.of(ModelOutputDefinition.builder()
                                                                   .name(Agent.OUTPUT_VARIABLE_NAME)
                                                                   .description("Strict JSON evaluator payload as text")
                                                                   .schema(objectMapper.createObjectNode().put("type",
                                                                                                               "string"))
                                                                   .build()),
                                                           List.of(new SystemPrompt(DEFAULT_AGENT_SESSION,
                                                                                    context.getRunId(),
                                                                                    DEFAULT_SYSTEM_INSTRUCTION,
                                                                                    false,
                                                                                    getClass().getSimpleName()),
                                                                   new UserPrompt(DEFAULT_AGENT_SESSION,
                                                                                  context.getRunId(),
                                                                                  renderPrompt(requestText, result),
                                                                                  LocalDateTime.now())),
                                                           Map.of(),
                                                           (tools, toolCall) -> null,
                                                           new NeverTerminateEarlyStrategy(),
                                                           List.of())
                    .join();
            return parseScore(modelOutput);
        }
        catch (Exception ignored) {
            return 0.0;
        }
    }

    protected final JsonNode normalizeJudgePayload(JsonNode rawOutput) {
        try {
            if (rawOutput.isTextual()) {
                return objectMapper.readTree(rawOutput.asText());
            }
            if (rawOutput.isObject()) {
                return rawOutput;
            }
            return null;
        }
        catch (Exception ignored) {
            return null;
        }
    }

    protected abstract double parseScore(ModelOutput output);

    protected final String promptTemplate() {
        return promptTemplate;
    }

    protected final JsonNode readModelOutputPayload(ModelOutput output) {
        if (output == null || output.getError() == null || output.getError().getErrorType() != ErrorType.SUCCESS) {
            return null;
        }

        final var rawOutput = output.getData() == null ? null : output.getData().get(Agent.OUTPUT_VARIABLE_NAME);
        if (rawOutput == null || rawOutput.isNull()) {
            return null;
        }
        return normalizeJudgePayload(rawOutput);
    }

    protected abstract String renderPrompt(String request, String answer);

    private ModelRunContext createModelRunContext(EvalExpectationContext<T> context) {
        final var runId = StringUtils.defaultIfBlank(context.getRunId(), "eval-run-" + UUID.randomUUID());
        return new ModelRunContext(getClass().getSimpleName(),
                                   runId,
                                   DEFAULT_AGENT_SESSION,
                                   getClass().getSimpleName(),
                                   AgentSetup.builder()
                                           .mapper(objectMapper)
                                           .model(evaluatorModel)
                                           .build(),
                                   new ModelUsageStats(),
                                   ProcessingMode.DIRECT);
    }
}
