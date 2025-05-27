package com.phonepe.sentinelai.toolbox.remotehttp;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * A generic specification for a remote HTTP call
 */
@Value
@Builder
public class HttpCallSpec {
    @NonNull
    HttpCallSpec.HttpMethod method;
    @NonNull
    String path;
    Map<String, List<String>> headers;
    String body;
    String contentType;

    public enum HttpMethod {
        GET,
        PUT,
        POST,
        HEAD,
        DELETE
    }
}
