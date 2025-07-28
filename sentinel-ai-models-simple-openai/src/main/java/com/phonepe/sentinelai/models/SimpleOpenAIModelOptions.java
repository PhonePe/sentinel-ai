package com.phonepe.sentinelai.models;

import lombok.Builder;
import lombok.Value;

import java.util.Objects;

/**
 * Class for model specific options for {@link SimpleOpenAIModel}.
 */
@Value
public class SimpleOpenAIModelOptions {
    public enum ToolChoice {
        REQUIRED, // Model will always call a tool. This is the default behavior.
        AUTO // Model will call a tool only if it needs to
    }

    public static final ToolChoice DEFAULT_TOOL_CHOICE = ToolChoice.REQUIRED;

    /**
     * Use this to set tool_choice parameter for OpenAI models to "required" or "auto".
     * By default, it is set to "required", which means the model will always call a tool. This is needed for the model
     * to call the output tool. However, it seems like some models like qwen (on vllm) are not calling the output tool
     * even then and the only way to make it call output tool is to set tool_choice to "auto".
     * Please refer to
     * <a href="https://platform.openai.com/docs/guides/function-calling/function-calling-behavior?api-mode=chat#additional-configurations">OpenAI documentation</a>
     * to understand more about the tool_choice parameter.
     * We do not support the option to set specific functions to be called by the model as tool.
     */
    ToolChoice toolChoice;

    @Builder
    public SimpleOpenAIModelOptions(ToolChoice toolChoice) {
        this.toolChoice = Objects.requireNonNullElse(toolChoice, ToolChoice.REQUIRED);
    }

    public SimpleOpenAIModelOptions merge(SimpleOpenAIModelOptions other) {
        if (other == null) {
            return this;
        }
        return new SimpleOpenAIModelOptions(
                Objects.requireNonNullElse(other.getToolChoice(), this.toolChoice)
        );
    }

}
