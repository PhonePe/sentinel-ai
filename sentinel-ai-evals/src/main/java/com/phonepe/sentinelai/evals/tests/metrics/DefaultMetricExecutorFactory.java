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

import com.fasterxml.jackson.databind.ObjectMapper;

import com.phonepe.sentinelai.core.model.Model;
import com.phonepe.sentinelai.core.utils.JsonUtils;
import com.phonepe.sentinelai.embedding.EmbeddingModel;

import java.util.concurrent.ExecutorService;

/**
 * @deprecated Use {@link MetricExecutorRegistry#withDefaults(EmbeddingModelIdentifier,
 *             EmbeddingModelFactory, LLMIdentifier, LLMModelFactory)} instead.
 */
@Deprecated
public class DefaultMetricExecutorFactory implements MetricExecutorFactory {

    private final MetricExecutorRegistry delegate;

    /**
     * Creates the deprecated factory backed by the default metric registry.
     *
     * @param embeddingModel       embedding model for similarity-based metrics
     * @param answerRelevanceModel judge model used for answer-relevance metrics
     * @deprecated Use the factory-based constructor instead.
     */
    @Deprecated
    public DefaultMetricExecutorFactory(EmbeddingModel embeddingModel,
                                        Model answerRelevanceModel) {
        this.delegate = MetricExecutorRegistry.withDefaults(embeddingModel,
                                                            answerRelevanceModel,
                                                            JsonUtils.createMapper());
    }

    /**
     * Creates the deprecated factory backed by the default metric registry.
     *
     * @param embeddingModel       embedding model for similarity-based metrics
     * @param answerRelevanceModel judge model used for answer-relevance metrics
     * @param objectMapper         mapper used by JSON-dependent metric executors
     * @deprecated Use the factory-based constructor instead.
     */
    @Deprecated
    public DefaultMetricExecutorFactory(EmbeddingModel embeddingModel,
                                        Model answerRelevanceModel,
                                        ObjectMapper objectMapper) {
        this.delegate = MetricExecutorRegistry.withDefaults(embeddingModel, answerRelevanceModel, objectMapper);
    }

    /**
     * Creates the deprecated factory backed by the registry, resolving models via factories.
     *
     * @param embeddingModelIdentifier identifies the embedding model
     * @param embeddingModelFactory    factory that creates the embedding model
     * @param llmIdentifier            identifies the LLM judge model
     * @param llmModelFactory          factory that creates the LLM judge model
     */
    public DefaultMetricExecutorFactory(EmbeddingModelIdentifier embeddingModelIdentifier,
                                        EmbeddingModelFactory embeddingModelFactory,
                                        LLMIdentifier llmIdentifier,
                                        LLMModelFactory llmModelFactory) {
        this.delegate = MetricExecutorRegistry.withDefaults(embeddingModelIdentifier,
                                                            embeddingModelFactory,
                                                            llmIdentifier,
                                                            llmModelFactory,
                                                            JsonUtils.createMapper());
    }

    /**
     * Creates the deprecated factory backed by the registry, resolving models via factories.
     *
     * @param embeddingModelIdentifier identifies the embedding model
     * @param embeddingModelFactory    factory that creates the embedding model
     * @param llmIdentifier            identifies the LLM judge model
     * @param llmModelFactory          factory that creates the LLM judge model
     * @param objectMapper             mapper used by JSON-dependent metric executors
     */
    public DefaultMetricExecutorFactory(EmbeddingModelIdentifier embeddingModelIdentifier,
                                        EmbeddingModelFactory embeddingModelFactory,
                                        LLMIdentifier llmIdentifier,
                                        LLMModelFactory llmModelFactory,
                                        ObjectMapper objectMapper) {
        this.delegate = MetricExecutorRegistry.withDefaults(embeddingModelIdentifier,
                                                            embeddingModelFactory,
                                                            llmIdentifier,
                                                            llmModelFactory,
                                                            objectMapper);
    }

    /**
     * Creates the deprecated factory backed by the default metric registry.
     *
     * @param answerRelevanceModel judge model used for answer-relevance metrics
     */
    public DefaultMetricExecutorFactory(Model answerRelevanceModel) {
        this.delegate = MetricExecutorRegistry.withDefaults(answerRelevanceModel, JsonUtils.createMapper());
    }

    /**
     * Creates the deprecated factory backed by the default metric registry.
     *
     * @param answerRelevanceModel        preferred judge model used for answer relevance
     * @param defaultAnswerRelevanceModel fallback judge model when the preferred one is {@code null}
     */
    public DefaultMetricExecutorFactory(Model answerRelevanceModel, Model defaultAnswerRelevanceModel) {
        this.delegate = MetricExecutorRegistry.withDefaults(
                                                            answerRelevanceModel != null ? answerRelevanceModel
                                                                    : defaultAnswerRelevanceModel,
                                                            JsonUtils.createMapper());
    }

    /**
     * Creates the deprecated factory backed by the default metric registry.
     *
     * @param answerRelevanceModel        preferred judge model used for answer relevance
     * @param defaultAnswerRelevanceModel fallback judge model when the preferred one is {@code null}
     * @param objectMapper                mapper used by JSON-dependent metric executors
     */
    public DefaultMetricExecutorFactory(Model answerRelevanceModel,
                                        Model defaultAnswerRelevanceModel,
                                        ObjectMapper objectMapper) {
        this.delegate = MetricExecutorRegistry.withDefaults(
                                                            answerRelevanceModel != null ? answerRelevanceModel
                                                                    : defaultAnswerRelevanceModel,
                                                            objectMapper);
    }

    /**
     * Creates the deprecated factory backed by the default metric registry.
     *
     * @param answerRelevanceModel judge model used for answer-relevance metrics
     * @param objectMapper         mapper used by JSON-dependent metric executors
     */
    public DefaultMetricExecutorFactory(Model answerRelevanceModel,
                                        ObjectMapper objectMapper) {
        this.delegate = MetricExecutorRegistry.withDefaults(answerRelevanceModel, objectMapper);
    }


    /**
     * Delegates metric executor creation to the underlying registry.
     *
     * @param metric metric definition to resolve
     * @param <R>    result/output type
     * @param <T>    input/request type
     * @return matching metric executor
     */
    @Override
    public <R, T> MetricExecutor<R, T> create(Metric<R, T> metric,
                                              ObjectMapper objectMapper,
                                              ExecutorService executorService) {
        return delegate.create(metric, objectMapper, executorService);
    }
}
