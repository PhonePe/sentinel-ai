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

import com.phonepe.sentinelai.embedding.EmbeddingModel;

/**
 * Factory that creates an {@link EmbeddingModel} from an {@link EmbeddingModelIdentifier}.
 *
 * <p>Implement this interface to wire a concrete embedding backend into the
 * {@link MetricExecutorRegistry}:
 *
 * <pre>{@code
 * EmbeddingModelFactory factory = identifier ->
 *         new HuggingfaceEmbeddingModel(identifier.modelId(), ...);
 *
 * MetricExecutorRegistry registry = MetricExecutorRegistry.withDefaults(
 *         new EmbeddingModelIdentifier("text-embedding-3-small"), factory,
 *         llmIdentifier, llmFactory);
 * }</pre>
 *
 * <p>Use {@link #noOp()} when no embedding model is available; metrics that require
 * one will be skipped with a warning.
 */
@FunctionalInterface
public interface EmbeddingModelFactory {

    /**
     * Returns a no-op factory that always returns {@code null}, causing
     * embedding-based metrics to be skipped with a warning.
     *
     * <p>This is the default used by {@link MetricExecutorRegistry} when no
     * factory is supplied.
     *
     * @return a no-op {@link EmbeddingModelFactory}
     */
    static EmbeddingModelFactory noOp() {
        return identifier -> null;
    }

    /**
     * Creates (or retrieves) an {@link EmbeddingModel} for the given identifier.
     *
     * @param identifier identifies the embedding model to create
     * @return the {@link EmbeddingModel} instance, or {@code null} to skip embedding-based metrics
     */
    EmbeddingModel create(EmbeddingModelIdentifier identifier);
}
