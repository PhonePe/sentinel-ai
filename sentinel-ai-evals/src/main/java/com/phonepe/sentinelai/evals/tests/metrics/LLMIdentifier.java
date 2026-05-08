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

/**
 * Identifies an LLM to be used by a {@link LLMModelFactory}.
 *
 * <p>The identifier carries the model name/id that the factory uses to resolve
 * the concrete {@link com.phonepe.sentinelai.core.model.Model} instance.
 *
 * <pre>{@code
 * LLMIdentifier id = new LLMIdentifier("gpt-4o");
 * MetricExecutorRegistry registry = MetricExecutorRegistry.withDefaults(
 *         new EmbeddingModelIdentifier("text-embedding-3-small"),
 *         myEmbeddingFactory,
 *         id,
 *         myLlmFactory);
 * }</pre>
 */
public record LLMIdentifier(String modelId) {

    /**
     * Constructs an {@code LLMIdentifier}.
     *
     * @param modelId the model name or id; must not be {@code null} or blank
     */
    public LLMIdentifier {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("modelId must not be null or blank");
        }
    }
}
