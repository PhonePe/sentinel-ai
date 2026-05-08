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

import com.phonepe.sentinelai.core.model.Model;

/**
 * Factory that creates a {@link Model} from an {@link LLMIdentifier}.
 *
 * <p>Implement this interface to wire a concrete LLM backend (e.g. {@code SimpleOpenAIModel})
 * into the {@link MetricExecutorRegistry}:
 *
 * <pre>{@code
 * LLMModelFactory factory = identifier -> new SimpleOpenAIModel<>(
 *         identifier.modelId(), openAiProvider, mapper, options);
 *
 * MetricExecutorRegistry registry = MetricExecutorRegistry.withDefaults(
 *         embeddingIdentifier, embeddingFactory,
 *         new LLMIdentifier("gpt-4o"), factory);
 * }</pre>
 *
 * <p>Use {@link #noOp()} when no LLM judge is available; metrics that require
 * one will be skipped with a warning.
 */
@FunctionalInterface
public interface LLMModelFactory {

    /**
     * Returns a no-op factory that always returns {@code null}, causing
     * LLM-judge metrics to be skipped with a warning.
     *
     * <p>This is the default used by {@link MetricExecutorRegistry} when no
     * factory is supplied.
     *
     * @return a no-op {@link LLMModelFactory}
     */
    static LLMModelFactory noOp() {
        return identifier -> null;
    }

    /**
     * Creates (or retrieves) a {@link Model} for the given identifier.
     *
     * @param identifier identifies the LLM to create
     * @return the {@link Model} instance, or {@code null} to skip LLM-dependent metrics
     */
    Model create(LLMIdentifier identifier);
}
