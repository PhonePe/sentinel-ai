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
