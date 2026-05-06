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

import org.apache.commons.lang3.StringUtils;

import com.phonepe.sentinelai.core.model.ModelOutput;

/**
 * Executor for {@link OutputRelevanceMetric} – performs the LLM-based answer-relevance calculation.
 *
 * @param <T> input/request type
 */
public class OutputRelevanceMetricExecutor<T>
        extends
        AbstractLlmJudgeMetricExecutor<OutputRelevanceMetric<T>, T> {

    public OutputRelevanceMetricExecutor(OutputRelevanceMetric<T> metric,
                                         com.phonepe.sentinelai.core.model.Model evaluatorModel) {
        super(metric, evaluatorModel);
    }

    @Override
    protected double parseScore(ModelOutput output) {
        final JsonNode judgeNode = readModelOutputPayload(output);
        if (judgeNode == null
                || !judgeNode.isObject()
                || judgeNode.size() != 2
                || !judgeNode.has(OutputRelevanceMetric.SCORE_FIELD)
                || !judgeNode.has(OutputRelevanceMetric.REASON_FIELD)) {
            return 0.0;
        }

        final var scoreNode = judgeNode.get(OutputRelevanceMetric.SCORE_FIELD);
        final var reasonNode = judgeNode.get(OutputRelevanceMetric.REASON_FIELD);
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
        return metric.getPromptTemplate()
                .replace(OutputRelevanceMetric.REQUEST_PLACEHOLDER, request)
                .replace(OutputRelevanceMetric.ANSWER_PLACEHOLDER, answer);
    }
}
