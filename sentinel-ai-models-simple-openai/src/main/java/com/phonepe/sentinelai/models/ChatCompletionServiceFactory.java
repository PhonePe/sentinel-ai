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

/**
 * This interface can be overridden to return different ChatCompletionServices
 * based on the model name.
 * This can be useful for cases where a Model or copies of it are used by different
 * agents but need to use different ChatCompletionServices instances. For example, with multiple provders
 * for different models. For example, OpenAI for some models, Open Router for some models etc.
 */
@FunctionalInterface
public interface ChatCompletionServiceFactory {
    ChatCompletionServices get(final String modelName);
}
