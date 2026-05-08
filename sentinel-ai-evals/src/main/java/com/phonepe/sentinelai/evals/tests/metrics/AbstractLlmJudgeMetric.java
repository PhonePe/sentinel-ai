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

import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * Base definition class for metrics that use a judge LLM to evaluate quality.
 *
 * This class carries only the configuration data (prompt template, mapper)
 * required to describe the metric. The evaluator model is passed to the executor.
 * All computation is performed by a corresponding
 * {@link AbstractLlmJudgeMetricExecutor} obtained via a {@link MetricExecutorFactory}.
 *
 * @param <T> input/request type
 */
public abstract class AbstractLlmJudgeMetric<T> implements Metric<String, T> {

    private final String promptTemplate;

    protected AbstractLlmJudgeMetric(String promptTemplate,
                                     List<String> requiredPlaceholders) {
        if (StringUtils.isBlank(promptTemplate)) {
            throw new IllegalArgumentException("promptTemplate cannot be null or blank");
        }
        if (requiredPlaceholders != null && requiredPlaceholders.stream().anyMatch(ph -> !promptTemplate.contains(
                                                                                                                  ph))) {
            throw new IllegalArgumentException("promptTemplate must contain placeholders: " + requiredPlaceholders);
        }
        this.promptTemplate = promptTemplate;
    }

    /**
     * Returns the prompt template rendered for the judge model.
     *
     * @return prompt template string
     */
    public String getPromptTemplate() {
        return promptTemplate;
    }
}
