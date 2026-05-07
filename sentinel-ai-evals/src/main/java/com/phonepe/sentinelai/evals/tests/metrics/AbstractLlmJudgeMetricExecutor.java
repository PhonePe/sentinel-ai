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
import com.phonepe.sentinelai.core.events.EventBus;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import com.phonepe.sentinelai.evals.tests.EvalExpectationContext;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

/**
 * Abstract executor for metrics that use a judge LLM to evaluate output quality.
 *
 * Holds the evaluator model and handles all shared LLM invocation logic.
 * Subclasses provide prompt rendering and score extraction specific to each
 * {@link AbstractLlmJudgeMetric} definition.
 *
 * @param <M> concrete {@link AbstractLlmJudgeMetric} definition type
 * @param <T> input/request type
 */
@Slf4j
public abstract class AbstractLlmJudgeMetricExecutor<M extends AbstractLlmJudgeMetric<T>, T>
        implements
        MetricExecutor<String, T> {

    private static final String DEFAULT_AGENT_SESSION = "llm-judge-metric-session";
    private static final String DEFAULT_SYSTEM_INSTRUCTION = "You are an evaluation judge.";

    protected final M metric;
    protected final ObjectMapper objectMapper;
    private final Model evaluatorModel;
    private final ExecutorService executorService;

    protected AbstractLlmJudgeMetricExecutor(M metric,
                                             Model evaluatorModel,
                                             ExecutorService executorService,
                                             ObjectMapper objectMapper) {
        this.metric = metric;
        this.evaluatorModel = evaluatorModel;
        this.executorService = executorService;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    /**
     * Calculates a score by rendering the judge prompt and invoking the evaluator model.
     *
     * @param result  agent output being judged
     * @param context evaluation context containing the original request
     * @return score produced by the judge model, or {@code 0.0} when input is insufficient
     */
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
            final var modelOutput = evaluatorModel
                    .compute(createModelRunContext(context),
                             List.of(ModelOutputDefinition.builder()
                                     .name(Agent.OUTPUT_VARIABLE_NAME)
                                     .description("Strict JSON evaluator payload as text")
                                     .schema(objectMapper.createObjectNode().put("type", "string"))
                                     .build()),
                             List.of(new SystemPrompt(DEFAULT_AGENT_SESSION,
                                                      context.getRunId(),
                                                      DEFAULT_SYSTEM_INSTRUCTION,
                                                      false,
                                                      metricClassName()),
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
        catch (Exception e) {
            log.warn("Exception while calculating metric {}: {}", metricName(), e.getMessage());
            throw new RuntimeException("Metric evaluation failed: " + metricName(), e);
        }
    }

    /**
     * Returns the metric name defined by the underlying metric definition.
     *
     * @return metric name
     */
    @Override
    public String metricName() {
        return metric.metricName();
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

    /**
     * Extract the numeric score from the judge model's output.
     *
     * @param output raw model output
     * @return score in range 0.0–1.0
     */
    protected abstract double parseScore(ModelOutput output);

    protected final JsonNode readModelOutputPayload(ModelOutput output) {
        if (output == null || output.getError() == null
                || output.getError().getErrorType() != ErrorType.SUCCESS) {
            return null;
        }

        final var rawOutput = output.getData() == null
                ? null
                : output.getData().get(Agent.OUTPUT_VARIABLE_NAME);
        if (rawOutput == null || rawOutput.isNull()) {
            return null;
        }
        return normalizeJudgePayload(rawOutput);
    }

    /**
     * Render the prompt to send to the judge model.
     *
     * @param request the user request text
     * @param answer  the agent answer text
     * @return rendered prompt string
     */
    protected abstract String renderPrompt(String request, String answer);

    private ModelRunContext createModelRunContext(EvalExpectationContext<T> context) {
        final var runId = StringUtils.defaultIfBlank(context.getRunId(), "eval-run-" + UUID.randomUUID());
        return new ModelRunContext(metricClassName(),
                                   runId,
                                   DEFAULT_AGENT_SESSION,
                                   metricClassName(),
                                   AgentSetup.builder()
                                           .mapper(objectMapper)
                                           .model(evaluatorModel)
                                           .eventBus(new EventBus())
                                           .outputGenerationMode(OutputGenerationMode.STRUCTURED_OUTPUT)
                                           .executorService(executorService)
                                           .build(),
                                   new ModelUsageStats(),
                                   ProcessingMode.DIRECT);
    }

    private String metricClassName() {
        return metric.getClass().getSimpleName();
    }
}
