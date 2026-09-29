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

package com.phonepe.sentinelai.models.provider;

import lombok.NonNull;
import okhttp3.Request;

import java.util.Map;

/**
 * Header-based {@link Auth}. Applies a fixed set of headers to every model call. Covers
 * Bearer tokens and api-key style headers.
 */
public class HeaderAuth implements Auth {

    private static final String BEARER_PREFIX = "Bearer ";

    private final Map<String, String> headers;

    private HeaderAuth(final Map<String, String> headers) {
        this.headers = Map.copyOf(headers);
    }

    /**
     * Builds a Bearer token auth for the given API key.
     *
     * @param apiKey API key sent as the Bearer token.
     * @return HeaderAuth carrying the Authorization header.
     */
    public static HeaderAuth bearer(@NonNull final String apiKey) {
        var effectiveApiKey = apiKey;
        if (!effectiveApiKey.startsWith(BEARER_PREFIX)) {
            effectiveApiKey = BEARER_PREFIX + apiKey;
        }
        return of("Authorization", effectiveApiKey);
    }

    /**
     * Builds an auth for a set of headers. Later headers replace earlier ones with the same
     * name.
     *
     * @param headers Headers to apply; must not be null.
     * @return HeaderAuth carrying the given headers.
     */
    public static HeaderAuth of(@NonNull final Map<String, String> headers) {
        return new HeaderAuth(headers);
    }

    /**
     * Builds an auth for a single header.
     *
     * @param headerName Header name.
     * @param value      Header value.
     * @return HeaderAuth carrying the given header.
     */
    public static HeaderAuth of(@NonNull final String headerName, @NonNull final String value) {
        return new HeaderAuth(Map.of(headerName, value));
    }

    @Override
    public void apply(final Request.Builder requestBuilder) {
        headers.forEach(requestBuilder::header);
    }
}
