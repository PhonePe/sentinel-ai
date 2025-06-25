package com.phonepe.sentinelai.toolbox.remotehttp;

import com.google.common.base.Strings;

/**
 * Can be used to resolve upstream URLs for HTTP calls corresponding to a given upstream identifier.
 * This is useful when the upstream URL is not known at compile time or program startup and needs to be resolved dynamically.
 *
 */
@FunctionalInterface
public interface UpstreamResolver {
    String resolve(String upstream);

    /**
     * Creates a direct upstream resolver that returns the given URL for an upstream identifier.
     * This is useful when you want to use a fixed URL for a given upstream.
     *
     * @param url The URL to return for any upstream
     * @return An instance of {@link UpstreamResolver} that returns back the given URL
     */
    static UpstreamResolver direct(String url) {
        return upstream -> {
            if (Strings.isNullOrEmpty(upstream)) {
                throw new IllegalArgumentException("Upstream cannot be null or empty");
            }
            return url;
        };
    }
}
