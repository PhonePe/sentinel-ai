package com.phonepe.sentinelai.toolbox.remotehttp;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.With;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * A generic specification for a remote HTTP call
 */
@Value
@Builder
@With
public class HttpCallSpec {
    @NonNull
    HttpCallSpec.HttpMethod method;
    @NonNull
    String path;
    Map<String, List<String>> headers;
    String body;
    String contentType;
    UnaryOperator<String> responseTransformer;

    public enum HttpMethod {
        GET,
        PUT,
        POST,
        HEAD,
        DELETE
    }
}
