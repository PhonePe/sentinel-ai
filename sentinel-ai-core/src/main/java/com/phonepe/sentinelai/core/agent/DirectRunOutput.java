package com.phonepe.sentinelai.core.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.phonepe.sentinelai.core.errors.SentinelError;
import com.phonepe.sentinelai.core.model.ModelUsageStats;
import lombok.Value;

import java.util.List;

/**
 * Output model for {@link #runDirect(AgentRunContext, ModelOutputDefinition, List)}
 */
@Value
public class DirectRunOutput {
    JsonNode data;
    ModelUsageStats stats;
    SentinelError error;

    public static DirectRunOutput success(final ModelUsageStats stats, final JsonNode data) {
        return new DirectRunOutput(data, stats, SentinelError.success());
    }

    public static DirectRunOutput error(final ModelUsageStats stats, final SentinelError error) {
        return new DirectRunOutput(null, stats, error);
    }
}
