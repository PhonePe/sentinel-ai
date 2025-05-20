package com.phonepe.sentinelai.toolbox.remotehttp;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

import java.util.Map;

/**
 *
 */
@Value
@Builder
@With
public class HttpToolMetadata {

    @Value
    public static class HttpToolParameterMeta {
        @NonNull
        String description;
        @NonNull
        HttpToolParameterType type;
    }

    String name;
    String description;
    Map<String, HttpToolParameterMeta> parameters;
}
