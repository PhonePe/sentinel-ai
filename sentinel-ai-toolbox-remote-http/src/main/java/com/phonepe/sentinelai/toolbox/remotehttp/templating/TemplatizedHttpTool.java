package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import com.phonepe.sentinelai.toolbox.remotehttp.HttpTool;
import com.phonepe.sentinelai.toolbox.remotehttp.HttpToolMetadata;
import lombok.*;
import lombok.extern.jackson.Jacksonized;

/**
 * A tool abstraction that makes an HTTP call to remote server
 */
@Value
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TemplatizedHttpTool extends HttpTool {

    @NonNull
    HttpCallTemplate template;

    @Builder
    @Jacksonized
    public TemplatizedHttpTool(@NonNull HttpToolMetadata metadata,
                               @NonNull HttpCallTemplate template) {
        super(metadata);
        this.template = template;
    }
}
