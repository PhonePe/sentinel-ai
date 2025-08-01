package com.phonepe.sentinelai.core.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;

import java.util.Map;

/**
 * Settings to change behaviour for model
 */
@Value
@Builder
@With
public class ModelSettings {
    /**
     * Maximum number of tokens to generate
     */
    Integer maxTokens;

    /**
     * Amount of randomness to inject in output. Varies from model to model. Lower generally means more predictable
     * output
     */
    Float temperature;

    /**
     * Defines the probabilistic sum of tokens that should be considered for each subsequent token. Range: 0-1.
     * So 0.1 means only the tokens comprising the top 10% probability mass are considered.
     * You should either alter `temperature` or `top_p`, but not both.
     */
    Float topP;

    /**
     * Timeout for calls
     */
    Float timeout;

    /**
     * Whether to call tools in parallel tool calls or not
     */
    Boolean parallelToolCalls;

    /**
     * Seed for random number generator. Can be used to make output more predictable.
     */
    Integer seed;

    /**
     * Penalty for adding new tokens to the output based on if they have already appeared in the output so far.
     */
    Float presencePenalty;

    /**
     * Penalty for adding new tokens to the output based on how many times they have appeared in the output so far.
     */
    Float frequencyPenalty;

    /**
     * Map of logit bias for tokens. Controls the likelihood of tokens being generated.
     */
    Map<String, Integer> logitBias;

    /**
     * Output generation mode to use for this model. Typically, other than OpenAI models, it is safer to leave it at
     * the default {@link OutputGenerationMode#TOOL_BASED}.
     */
    OutputGenerationMode outputGenerationMode;
}
