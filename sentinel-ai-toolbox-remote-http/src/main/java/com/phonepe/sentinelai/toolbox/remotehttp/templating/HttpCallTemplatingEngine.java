package com.phonepe.sentinelai.toolbox.remotehttp.templating;

import java.util.Map;

/**
 * Interface for converting {@link HttpCallTemplate.Template} to a string representation.
 */
public interface HttpCallTemplatingEngine {
    String convert(final HttpCallTemplate.Template template, Map<String, Object> context);
}
