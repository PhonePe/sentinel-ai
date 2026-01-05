package com.phonepe.sentinelai.configuredagents;

import com.phonepe.sentinelai.core.model.ModelSettings;
import com.phonepe.sentinelai.core.model.OutputGenerationMode;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Configuration related to the model used by a registered agent. This will be used to override the models etc
 * being passed in the {@link com.phonepe.sentinelai.core.agent.AgentSetup} from parent.
 */
@Value
@Builder
@Jacksonized
public class ModelConfiguration {
    @NonNull
    String name;
    ModelSettings settings;
    OutputGenerationMode outputGenerationMode;
}
