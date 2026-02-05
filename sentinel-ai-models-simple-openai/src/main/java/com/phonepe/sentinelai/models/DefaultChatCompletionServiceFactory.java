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

package com.phonepe.sentinelai.models;

import io.github.sashirestela.openai.service.ChatCompletionServices;

import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The default Chat Completion Service Provider factory.
 * It provides the functionality to register completion service providers
 * against model names and retrieve them as needed. A default provider can be set
 * to be used when no specific provider is found for a given model name. If no
 * default provider is set and a model name is not found, an exception is thrown.
 * This is useful for managing multiple OpenAI model providers in applications
 * that interact with different models. For example, when used with Agent Registry where
 * sub-agents might use different models.
 */
@NoArgsConstructor
public class DefaultChatCompletionServiceFactory implements ChatCompletionServiceFactory {
    private final AtomicReference<ChatCompletionServices> defaultProvider = new AtomicReference<>();
    private final Map<String, ChatCompletionServices> providers = new ConcurrentHashMap<>();

    public DefaultChatCompletionServiceFactory(@NonNull final ChatCompletionServices defaultProvider) {
        this.defaultProvider.set(defaultProvider);
    }

    /**
     * Retrieve the ChatCompletionServices provider for the given model name.
     * If no provider is found for the model name, the default provider is returned.
     * If no default provider is set, an exception is thrown.
     *
     * @param modelName the model name
     * @return the ChatCompletionServices provider
     * @throws NullPointerException if no provider is found for the model name and no default provider is set
     */
    @Override
    public ChatCompletionServices get(String modelName) {
        return Objects.requireNonNull(providers.getOrDefault(modelName,
                                                             defaultProvider
                                                                     .get()),
                                      "No ChatCompletionServices provider found for model name: " + modelName);
    }

    public DefaultChatCompletionServiceFactory registerDefaultProvider(@NonNull final ChatCompletionServices defaultProvider) {
        this.defaultProvider.set(defaultProvider);
        return this;
    }

    /**
     * Register a ChatCompletionServices provider against a model name.
     *
     * @param name     the model name (ex: "gpt-4", "gpt-3.5-turbo" etc.). This is the same as the modelName parameter
     *                 passed in the constructor to {@link SimpleOpenAIModel}
     * @param provider the ChatCompletionServices provider instance
     * @return the current DefaultChatCompletionServiceFactory instance for method chaining
     */
    public DefaultChatCompletionServiceFactory registerProvider(@NonNull final String name,
                                                                @NonNull final ChatCompletionServices provider) {
        this.providers.put(name, provider);
        return this;
    }
}
