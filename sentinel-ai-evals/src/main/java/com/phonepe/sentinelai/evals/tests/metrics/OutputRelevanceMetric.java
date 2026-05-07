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

import java.util.List;
import java.util.Objects;

/**
 * Definition for a metric that uses a judge LLM to evaluate answer relevance.
 *
 * Carries the model, prompt template and mapper.
 * Computation is performed by {@code OutputRelevanceMetricExecutor}.
 *
 * The judge model must return strict JSON:
 * {"score": 0.0-1.0, "reason": "..."}
 *
 * @param <T> input/request type
 */
public class OutputRelevanceMetric<T> extends AbstractLlmJudgeMetric<T> {

    public static final String REQUEST_PLACEHOLDER = "{request}";
    public static final String ANSWER_PLACEHOLDER = "{answer}";
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

    /**
     * Creates an answer-relevance metric using the built-in judge prompt.
     */
    public OutputRelevanceMetric() {
        this(DEFAULT_PROMPT_TEMPLATE);
    }

    /**
     * Creates an answer-relevance metric with a custom judge prompt.
     *
     * @param promptTemplate prompt template containing {@value #REQUEST_PLACEHOLDER} and
     *                       {@value #ANSWER_PLACEHOLDER}
     */
    public OutputRelevanceMetric(String promptTemplate) {
        super(Objects.requireNonNullElse(promptTemplate, DEFAULT_PROMPT_TEMPLATE),
              List.of(REQUEST_PLACEHOLDER, ANSWER_PLACEHOLDER));
    }

    /**
     * Returns the stable display name of this metric.
     *
     * @return metric name
     */
    @Override
    public String metricName() {
        return "AnswerRelevance";
    }
}
