package com.phonepe.sentinelai.models;

import java.util.Objects;

import lombok.Builder;
import lombok.Value;

/**
 * Class for model specific options for {@link SimpleOpenAIModel}.
 */
@Value
public class SimpleOpenAIModelOptions {
    public static final ToolChoice DEFAULT_TOOL_CHOICE = ToolChoice.AUTO;
    public static final TokenCountingConfig DEFAULT_TOKEN_COUNTING_CONFIG = TokenCountingConfig.DEFAULT;

    public static final SimpleOpenAIModelOptions DEFAULT = new SimpleOpenAIModelOptions(
            DEFAULT_TOOL_CHOICE,
            DEFAULT_TOKEN_COUNTING_CONFIG);

    public enum ToolChoice {
        REQUIRED, // Model will always call a tool. This is the default behavior.
        AUTO // Model will call a tool only if it needs to
    }


    /**
     * Use this to set tool_choice parameter for OpenAI models to "required" or "auto".
     * By default, it is set to "required", which means the model will always call a tool. This is needed for the model
     * to call the output tool. However, it seems like some models like qwen (on vllm) are not calling the output tool
     * even then and the only way to make it call output tool is to set tool_choice to "auto".
     * Please refer to
     * <a href="https://platform.openai.com/docs/guides/function-calling/function-calling-behavior?api-mode=chat#additional-configurations">OpenAI documentation</a>
     * to understand more about the tool_choice parameter.
     */
    ToolChoice toolChoice;

    /**
     * Configuration for token counting.
     *
     * This config will be used by {@link OpenAICompletionsTokenCounter} to estimate token counts for messages.
     */
    TokenCountingConfig tokenCountingConfig;

    @Builder
    public SimpleOpenAIModelOptions(ToolChoice toolChoice, TokenCountingConfig tokenCountingConfig) {
        this.toolChoice = Objects.requireNonNullElse(toolChoice, ToolChoice.REQUIRED);
        this.tokenCountingConfig = Objects.requireNonNullElse(tokenCountingConfig, TokenCountingConfig.DEFAULT);
    }

    public SimpleOpenAIModelOptions merge(SimpleOpenAIModelOptions other) {
        if (other == null) {
            return this;
        }
        return new SimpleOpenAIModelOptions(
                Objects.requireNonNullElse(other.getToolChoice(), this.toolChoice),
                Objects.requireNonNullElse(other.getTokenCountingConfig(), this.tokenCountingConfig)
        );
    }

}
