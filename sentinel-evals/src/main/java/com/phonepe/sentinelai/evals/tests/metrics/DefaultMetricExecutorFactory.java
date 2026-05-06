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

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import com.phonepe.sentinelai.core.agent.Agent;
import com.phonepe.sentinelai.core.agent.ModelOutputDefinition;
import com.phonepe.sentinelai.core.agent.ToolRunner;
import com.phonepe.sentinelai.core.agentmessages.AgentMessage;
import com.phonepe.sentinelai.core.earlytermination.EarlyTerminationStrategy;
import com.phonepe.sentinelai.core.hooks.AgentMessagesPreProcessor;
import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.model.ModelOutput;
import com.phonepe.sentinelai.core.model.ModelRunContext;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import com.phonepe.sentinelai.core.tools.ExecutableTool;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Default concrete implementation of {@link MetricExecutorFactory}.
 *
 * Maps each built-in {@link Metric} definition to its corresponding {@link MetricExecutor}.
 * Custom metric types can be supported by subclassing or providing an alternative factory.
 */
public class DefaultMetricExecutorFactory implements MetricExecutorFactory {

    private static final Model DEFAULT_ANSWER_RELEVANCE_MODEL = new DefaultAnswerRelevanceModel();

    private static final class DefaultAnswerRelevanceModel implements Model {
        @Override
        public CompletableFuture<ModelOutput> compute(ModelRunContext context,
                                                      Collection<ModelOutputDefinition> outputDefinitions,
                                                      List<AgentMessage> oldMessages,
                                                      Map<String, ExecutableTool> tools,
                                                      ToolRunner toolRunner,
                                                      EarlyTerminationStrategy earlyTerminationStrategy,
                                                      List<AgentMessagesPreProcessor> agentMessagesPreProcessors) {
            final var data = JsonNodeFactory.instance.objectNode();
            data.put(Agent.OUTPUT_VARIABLE_NAME,
                     "{\"score\":0.0,\"reason\":\"No answer relevance evaluator model configured\"}");
            final var safeMessages = oldMessages == null ? List.<AgentMessage>of() : oldMessages;
            return CompletableFuture.completedFuture(ModelOutput.success(data,
                                                                         List.of(),
                                                                         safeMessages,
                                                                         new ModelUsageStats()));
        }
    }

    private final Model answerRelevanceModel;

    public DefaultMetricExecutorFactory() {
        this(null, DEFAULT_ANSWER_RELEVANCE_MODEL);
    }

    public DefaultMetricExecutorFactory(Model answerRelevanceModel) {
        this(answerRelevanceModel, DEFAULT_ANSWER_RELEVANCE_MODEL);
    }

    public DefaultMetricExecutorFactory(Model answerRelevanceModel, Model defaultAnswerRelevanceModel) {
        this.answerRelevanceModel = answerRelevanceModel != null
                ? answerRelevanceModel
                : Objects.requireNonNull(defaultAnswerRelevanceModel, "defaultAnswerRelevanceModel cannot be null");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric) {
        if (metric instanceof OutputSimilarityMetric<?> m) {
            return (MetricExecutor<R, T>) new OutputSimilarityMetricExecutor<>((OutputSimilarityMetric<T>) m);
        }
        if (metric instanceof OutputRelevanceBySimilarityMetric<?> m) {
            return (MetricExecutor<R, T>) new OutputRelevanceBySimilarityMetricExecutor<>(
                                                                                          (OutputRelevanceBySimilarityMetric<T>) m);
        }
        if (metric instanceof OutputRelevanceMetric<?> m) {
            return (MetricExecutor<R, T>) new OutputRelevanceMetricExecutor<>((OutputRelevanceMetric<T>) m,
                                                                              answerRelevanceModel);
        }
        throw new IllegalArgumentException(
                                           "No MetricExecutor registered for metric type: " + metric.getClass()
                                                   .getName());
    }
}
