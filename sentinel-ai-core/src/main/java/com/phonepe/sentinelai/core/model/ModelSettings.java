package com.phonepe.sentinelai.core.model;

import lombok.Builder;
import lombok.Value;
import lombok.With;
import lombok.Builder.Default;

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
     * Reasoning effort by the model. Will be model specific. Might cause errors if unsupported.
     * Check model documentation.
     */
    Reasoning reasoning;

    /**
     * Attributes of the model.
     * Context window size, token counting overheads etc.
     */
    @Default
    ModelAttributes modelAttributes = ModelAttributes.DEFAULT_MODEL_ATTRIBUTES;

    /**
     * Method to merge two model settings objects where the provided values in the rhs param are set if not null
     * else the lhs values are retained.
     * @param lhs Left hand side model settings
     * @param rhs Right hand side model settings
     * @return Merged model settings
     */
    public static ModelSettings merge(ModelSettings lhs, ModelSettings rhs) {
        if (lhs == null) {
            return rhs;
        }
        if (rhs == null) {
            return lhs;
        }
        return new ModelSettings(
                rhs.getMaxTokens() != null ? rhs.getMaxTokens() : lhs.getMaxTokens(),
                rhs.getTemperature() != null ? rhs.getTemperature() : lhs.getTemperature(),
                rhs.getTopP() != null ? rhs.getTopP() : lhs.getTopP(),
                rhs.getTimeout() != null ? rhs.getTimeout() : lhs.getTimeout(),
                rhs.getParallelToolCalls() != null ? rhs.getParallelToolCalls() : lhs.getParallelToolCalls(),
                rhs.getSeed() != null ? rhs.getSeed() : lhs.getSeed(),
                rhs.getPresencePenalty() != null ? rhs.getPresencePenalty() : lhs.getPresencePenalty(),
                rhs.getFrequencyPenalty() != null ? rhs.getFrequencyPenalty() : lhs.getFrequencyPenalty(),
                rhs.getLogitBias() != null ? rhs.getLogitBias() : lhs.getLogitBias(),
                rhs.getReasoning() != null ? rhs.getReasoning() : lhs.getReasoning(),
                rhs.getModelAttributes() != ModelAttributes.DEFAULT_MODEL_ATTRIBUTES ? rhs.getModelAttributes() : lhs.getModelAttributes()
        );
    }

}
