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
     * Root URL of the provider endpoint including any path prefix, for example
     * {@code https://api.openai.com/v1}. The protocol appends only its path after it.
     */
    @NonNull
    private final String baseUrl;

    /**
     * Wire protocol of the provider: request body shape, response decoding and endpoint
     * path.
     */
    @NonNull
    private final WireProtocol protocol;

    /**
     * Additional protocols this endpoint can speak; may be empty. The default
     * {@link #protocol} is always supported. Models select one of these through the
     * model-level protocol override.
     */
    @Builder.Default
    private final List<WireProtocol> supportedProtocols = List.of();

    /**
     * Authentication applied to every request; null means no auth is applied by the model.
     * Transport level auth (OkHttp interceptors) stays available.
     */
    private final Auth auth;

    /**
     * Request transformers applied to every request after authentication and before
     * serialization; may be empty. See {@link RequestTransformer} for the contract.
     */
    @Builder.Default
    private final List<RequestTransformer> requestTransformers = List.of();

    /**
     * Validates the requested protocol against this endpoint.
     *
     * @param requested protocol the model wants to use; null means the default
     * @return the default protocol when {@code requested} is null, else {@code requested}
     * @throws IllegalArgumentException when {@code requested} is neither the default protocol
     *                                  nor listed in {@link #supportedProtocols}
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
