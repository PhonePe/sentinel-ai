package com.phonepe.sentinelai.toolbox.remotehttp;

import com.phonepe.sentinelai.toolbox.remotehttp.templating.HttpCallTemplate;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * A tool abstraction that makes an HTTP call to remote server
 */
@Value
@Builder
public class HttpTool {
    @NonNull
    HttpToolMetadata toolConfig;
    @NonNull
    HttpCallTemplate template;
}
