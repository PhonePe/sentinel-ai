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

import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;

import java.util.List;
import java.util.Objects;

/**
 * Uses an evaluator model to judge whether an answer is relevant to the given request.
 *
 * The judge model must return strict JSON payload with exactly two fields:
 * {"score": 0.0-1.0, "reason": "..."}
 *
 * @param <T> input/request type
 */
public class OutputRelevanceMetric<T> extends AbstractLlmJudgeMetric<T> {
    private static final String REQUEST_PLACEHOLDER = "{request}";
    private static final String ANSWER_PLACEHOLDER = "{answer}";

    public static final String SCORE_FIELD = "score";
    public static final String REASON_FIELD = "reason";
    public static final String DEFAULT_PROMPT_TEMPLATE = """
            Evaluate whether the assistant answer is relevant to the user request.

            Return ONLY valid JSON with this exact shape and no extra fields:
            {"score": 0.0, "reason": "brief reason"}

            Scoring rubric:
            - intent coverage
            - factual alignment to the request scope
            - no off-topic content

            User request:
            {request}

            Assistant answer:
            {answer}
            """;

    public OutputRelevanceMetric(Model evaluatorModel) {
        this(evaluatorModel, DEFAULT_PROMPT_TEMPLATE);
    }

    public OutputRelevanceMetric(Model evaluatorModel, String promptTemplate) {
        this(evaluatorModel, promptTemplate, new ObjectMapper());
    }

    public OutputRelevanceMetric(Model evaluatorModel,
                                 String promptTemplate,
                                 ObjectMapper objectMapper) {
        super(evaluatorModel,
              Objects.requireNonNullElse(promptTemplate, DEFAULT_PROMPT_TEMPLATE),
              objectMapper,
              List.of(REQUEST_PLACEHOLDER, ANSWER_PLACEHOLDER));
    }

    @Override
    public String metricName() {
        return "AnswerRelevance";
    }

    @Override
    protected double parseScore(ModelOutput output) {
        final JsonNode judgeNode = readModelOutputPayload(output);
        if (judgeNode == null
                || !judgeNode.isObject()
                || judgeNode.size() != 2
                || !judgeNode.has(SCORE_FIELD)
                || !judgeNode.has(REASON_FIELD)) {
            return 0.0;
        }

        final var scoreNode = judgeNode.get(SCORE_FIELD);
        final var reasonNode = judgeNode.get(REASON_FIELD);
        if (scoreNode == null || !scoreNode.isNumber() || reasonNode == null || !reasonNode.isTextual()) {
            return 0.0;
        }

        final var score = scoreNode.doubleValue();
        final var reason = reasonNode.textValue();
        if (Double.isNaN(score)
                || Double.isInfinite(score)
                || score < 0.0
                || score > 1.0
                || StringUtils.isBlank(reason)) {
            return 0.0;
        }
        return score;
    }

    @Override
    protected String renderPrompt(String request, String answer) {
        return promptTemplate().replace(REQUEST_PLACEHOLDER, request)
                .replace(ANSWER_PLACEHOLDER, answer);
    }
}
