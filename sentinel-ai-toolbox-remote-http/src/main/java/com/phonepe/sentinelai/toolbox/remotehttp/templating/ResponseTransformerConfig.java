package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import lombok.Builder;
import lombok.Value;

/**
 *
 */
@Value
@Builder
public class ResponseTransformerConfig {
    public enum Type {
        JOLT
    }
    Type type;
    String config;
}
