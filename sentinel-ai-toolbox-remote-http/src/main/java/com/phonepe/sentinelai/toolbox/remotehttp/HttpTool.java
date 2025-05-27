package com.phonepe.sentinelai.toolbox.remotehttp;

import lombok.*;

/**
 * A tool abstraction that makes an HTTP call to remote server
 */
@Data
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class HttpTool {
    @NonNull
    HttpToolMetadata metadata;
}
