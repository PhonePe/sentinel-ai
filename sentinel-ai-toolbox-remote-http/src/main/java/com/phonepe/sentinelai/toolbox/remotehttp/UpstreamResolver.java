/*
 * Copyright (c) 2025 Original Author(s), PhonePe India Pvt. Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.phonepe.sentinelai.toolbox.remotehttp;

import com.google.common.base.Strings;

/**
 * Can be used to resolve upstream URLs for HTTP calls corresponding to a given upstream identifier.
 * This is useful when the upstream URL is not known at compile time or program startup and needs to be resolved
 * dynamically.
 *
 */
@FunctionalInterface
public interface UpstreamResolver {
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

    String resolve(String upstream);
}
