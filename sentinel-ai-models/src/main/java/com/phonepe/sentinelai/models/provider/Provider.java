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

import com.phonepe.sentinelai.models.wire.WireProtocol;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

import java.util.List;

/**
 * Everything about the remote model endpoint: base URL, endpoint prefix, authentication and
 * how the calls travel.
 * <p>
 * Endpoints that speak more than one wire protocol (for example Copilot) list every protocol
 * in {@link #supportedProtocols} and keep {@link #protocol} as the default. Models select any
 * supported protocol through the model-level protocol override; see
 * {@link com.phonepe.sentinelai.models.ConfiguredModel}.
 * </p>
 */
@Getter
@Builder
public class Provider {

    /**
     * Endpoint path prefix meaning no prefix: the URL is base URL plus protocol path.
     */
    public static final String NO_ENDPOINT_PREFIX = "";

    /**
     * Root URL of the API host, for example {@code https://api.openai.com}.
     */
    @NonNull
    private final String baseUrl;

    /**
     * Endpoint path prefix between the base URL and the protocol path; null means the protocol
     * default {@link WireProtocol#DEFAULT_ENDPOINT_PREFIX}.
     */
    private final String endpointPrefix;

    @NonNull
    private final WireProtocol protocol;

    /**
     * Additional protocols this endpoint speaks; the default {@link #protocol} is always supported.
     */
    @Builder.Default
    private final List<WireProtocol> supportedProtocols = List.of();

    /**
     * Auth applied to every request; null means none (OkHttp interceptors still work).
     */
    private final Auth auth;

    @Builder.Default
    private final List<RequestTransformer> requestTransformers = List.of();

    /**
     * @return The default protocol when {@code requested} is null, else {@code requested};
     *         throws when the requested protocol is not supported.
     */
    public WireProtocol protocolFor(final WireProtocol requested) {
        if (requested == null) {
            return protocol;
        }
        if (requested.equals(protocol) || supportedProtocols.contains(requested)) {
            return requested;
        }
        throw new IllegalArgumentException(
                                           "Protocol " + requested.getClass().getSimpleName()
                                                   + " is not supported by this provider. "
                                                   + "Supported: default " + protocol.getClass().getSimpleName()
                                                   + (supportedProtocols.isEmpty() ? " only" : ", plus "
                                                           + supportedProtocols.size() + " more"));
    }
}
